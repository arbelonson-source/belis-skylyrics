# Beli's SkyLyrics: Windows media helper.
#
# The mod starts this hidden, for as long as the game runs, to read what Windows' media controls
# are playing (the panel that pops up when you press a media or volume key). Spotify, browsers,
# Media Player, Apple Music and most other players report to them.
#
# The mod writes one numbered command per line. Each one gets exactly one line of JSON back,
# carrying the same number:
#   7 poll                -> {"seq":7,"ok":true,"sessions":[{"index":0,"id":"Spotify.exe",...}, ...]}
#   8 toggle INDEX ID     -> {"seq":8,"ok":true}       (also: next, prev)
#   9 seek MS INDEX ID    -> {"seq":9,"ok":true}
# INDEX is the player's place in the last poll: browsers give every tab the same ID, so the ID alone
# can't say which tab is meant. A problem answers {"seq":N,"ok":false,"error":"..."}.
# When the game closes our input, we stop. If Windows' media controls stop working for good, we exit
# with code 2 so the mod starts a fresh helper.
#
# Keep this file ASCII: Windows PowerShell reads a script without a BOM in the local code page.

param(
	[string]$CoverDir,
	# Tests pass a stand-in for Windows' media manager; normally it comes from Windows
	$Manager
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding $false
$stdin = New-Object System.IO.StreamReader ([Console]::OpenStandardInput()), $utf8
$stdout = New-Object System.IO.StreamWriter ([Console]::OpenStandardOutput()), $utf8
$stdout.AutoFlush = $true
try {
	Add-Type -AssemblyName System.Web
} catch {
	$stdout.WriteLine('{"ready":false,"code":"no_web","error":"System.Web is missing from Windows"}')
	exit 1
}

$seq = 0
# Filled in when we talk to the real Windows: a helper to wait on its operations, and their result types
$asTask = $null
$ManagerType = $null
$PropertiesType = $null
$StreamType = $null

function J($text) {
	[System.Web.HttpUtility]::JavaScriptStringEncode([string]$text, $true)
}

function Reply([string]$body) {
	$stdout.WriteLine('{"seq":' + $script:seq + ',' + $body + '}')
}

function Fail($message) {
	Reply ('"ok":false,"error":' + (J $message))
}

# An error the mod shows translated: CODE is stable, DETAIL is the technical part
function FailCode([string]$code, [string]$detail) {
	Reply ('"ok":false,"code":' + (J $code) + ',"detail":' + (J $detail) + ',"error":' + (J ($code + ': ' + $detail)))
}

# The useful part of an error: .NET wraps the real one in "One or more errors occurred."
function Why($exception) {
	while ($null -ne $exception.InnerException) {
		$exception = $exception.InnerException
	}
	return $exception.Message
}

# Turns a Windows operation (or, in the tests, a .NET task) into a .NET task without waiting for it
function Start-Op($operation, [Type]$resultType) {
	if ($operation -is [System.Management.Automation.PSObject]) {
		$operation = $operation.psobject.BaseObject
	}
	if ($operation -is [System.Threading.Tasks.Task]) {
		return $operation
	}
	return $script:asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
}

# Waits for a Windows operation and returns its result
function Await($operation, [Type]$resultType, [int]$timeoutMs = 1500) {
	$task = Start-Op $operation $resultType
	if (-not $task.Wait($timeoutMs)) {
		throw 'Windows took too long to answer'
	}
	return $task.Result
}

function Ms([TimeSpan]$time) {
	return [long]$time.TotalMilliseconds
}

# Unix time in ms; 0 when the player never said
function Unix([DateTimeOffset]$time) {
	if ($time.Year -lt 2000) {
		return 0
	}
	return $time.ToUnixTimeMilliseconds()
}

# Playback speed (1.0 normal, 1.5 for a 1.5x video); players that don't say get 1.0
function Rate($info) {
	$rate = 1.0
	try {
		if ($null -ne $info.PlaybackRate) {
			$rate = [double]$info.PlaybackRate
		}
	} catch {
		$rate = 1.0
	}
	if ([double]::IsNaN($rate) -or [double]::IsInfinity($rate) -or $rate -le 0) {
		$rate = 1.0
	}
	return $rate.ToString('R', [System.Globalization.CultureInfo]::InvariantCulture)
}

# Covers are saved as files the mod can open. Song key -> file. Players often switch the title a moment
# before the cover, so a new song's cover is only read once it has been playing for a little while, and
# read once more a few seconds later in case the first one was still the previous song's.
# The mod waits only 4 s for an answer, so cover work gets a small total budget per poll and every step of it
# has a time limit: a cover that hangs is given up on, never waited for.
$covers = @{}
$coverOrder = New-Object System.Collections.Generic.Queue[string]
$coverCount = 0
$firstSeen = @{}
$coverTries = @{}
$coverChecked = @{}
$retired = New-Object System.Collections.Generic.Queue[string]
$coverBudgetMs = 1000
# What the last poll said about each player (the JSON), and which of those were only carried over
$lastGood = @{}
$lastStale = @{}

# Deletes a cover file, but only one inside the covers folder this script owns
function Remove-Cover([string]$path) {
	if (-not $path -or -not $CoverDir) {
		return
	}
	try {
		$root = [System.IO.Path]::GetFullPath($CoverDir).TrimEnd([char[]]'\/')
		$full = [System.IO.Path]::GetFullPath($path)
		if ($full.StartsWith($root + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
			Remove-Item -LiteralPath $full -ErrorAction SilentlyContinue
		}
	} catch {
		# Not ours, or already gone
	}
}

# Saves the player's current thumbnail to a new file and returns its path; '' if there is none or it failed;
# $null if this poll's cover time is used up (not a failure). Never waits more than what is left of the budget.
function Read-Cover($media) {
	$left = [int]$script:coverBudgetMs
	if ($left -lt 150) {
		return $null
	}
	$watch = [System.Diagnostics.Stopwatch]::StartNew()
	$path = ''
	$hung = $false
	$source = $null
	$file = $null
	try {
		$stream = Await ($media.Thumbnail.OpenReadAsync()) $StreamType ([Math]::Min(500, $left))
		$script:coverCount++
		$path = Join-Path $CoverDir ('cover-' + $PID + '-' + $script:coverCount + '.img')
		if ($stream -is [System.IO.Stream]) {
			$source = $stream # the tests' stand-in; Windows gives a Windows stream that needs converting
		} else {
			$source = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
		}
		$file = [System.IO.File]::Create($path)
		$copy = $source.CopyToAsync($file)
		$rest = [int][Math]::Max(100, $left - $watch.ElapsedMilliseconds)
		if (-not $copy.Wait($rest)) {
			$hung = $true
			throw 'The cover took too long to copy'
		}
		$file.Dispose()
		$file = $null
		$source.Dispose()
		$source = $null
		if ((Get-Item -LiteralPath $path).Length -eq 0) {
			Remove-Cover $path
			$path = ''
		}
	} catch {
		if (-not $hung) {
			# A copy that hangs is left alone: closing a stream that is being read can hang too
			try { if ($null -ne $file) { $file.Dispose() } } catch { }
			try { if ($null -ne $source) { $source.Dispose() } } catch { }
		}
		Remove-Cover $path
		$path = ''
	}
	$script:coverBudgetMs = [int]$script:coverBudgetMs - [int]$watch.ElapsedMilliseconds
	return $path
}

function Same-Cover([string]$a, [string]$b) {
	try {
		$x = [System.IO.File]::ReadAllBytes($a)
		$y = [System.IO.File]::ReadAllBytes($b)
		if ($x.Length -ne $y.Length) {
			return $false
		}
		$sha = [System.Security.Cryptography.SHA256]::Create()
		return ([BitConverter]::ToString($sha.ComputeHash($x)) -eq [BitConverter]::ToString($sha.ComputeHash($y)))
	} catch {
		return $false
	}
}

function Cover($media, [string]$key) {
	if ($covers.ContainsKey($key)) {
		$known = [string]$covers[$key]
		# The first read can catch the previous song's cover; look once more a few seconds in
		if ($known -and -not $coverChecked.ContainsKey($key) -and $firstSeen.ContainsKey($key) -and $null -ne $media.Thumbnail -and
			($firstSeen[$key].AddMilliseconds(5000) -lt [DateTime]::UtcNow)) {
			$again = Read-Cover $media
			if ($null -ne $again) {
				$coverChecked[$key] = $true
				if ($again) {
					if (Same-Cover $known $again) {
						Remove-Cover $again
					} else {
						$covers[$key] = $again
						$retired.Enqueue($known) # the mod may still be reading it; deleted a little later
						while ($retired.Count -gt 6) {
							Remove-Cover ($retired.Dequeue())
						}
						return $again
					}
				}
			}
		}
		return $known
	}
	if ($null -eq $media.Thumbnail -or -not $CoverDir) {
		return '' # browsers often add the cover a moment after the title
	}
	$now = [DateTime]::UtcNow
	if (-not $firstSeen.ContainsKey($key)) {
		if ($firstSeen.Count -gt 200) {
			$firstSeen.Clear()
			$coverTries.Clear()
		}
		$firstSeen[$key] = $now
	}
	if (($now - $firstSeen[$key]).TotalMilliseconds -lt 1500) {
		return ''
	}
	$path = Read-Cover $media
	if ($null -eq $path) {
		return '' # no time left in this poll; the next one tries
	}
	if (-not $path) {
		# Try again on the next polls; give up on this song after a few tries
		$coverTries[$key] = 1 + [int]$coverTries[$key]
		if ($coverTries[$key] -ge 3) {
			$covers[$key] = ''
		}
		return ''
	}
	$covers[$key] = $path
	$coverOrder.Enqueue($key)
	while ($coverOrder.Count -gt 6) {
		$old = $coverOrder.Dequeue()
		Remove-Cover ([string]$covers[$old])
		$covers.Remove($old)
		$coverChecked.Remove($old)
	}
	if ($covers.Count -gt 200) {
		# Only songs without a cover pile up here; forget those, and delete the files of any others it forgets
		foreach ($k in @($covers.Keys)) {
			if (-not $covers[$k]) {
				$covers.Remove($k)
			}
		}
		if ($covers.Count -gt 200) {
			foreach ($k in @($covers.Keys)) {
				Remove-Cover ([string]$covers[$k])
			}
			$covers.Clear()
			$coverOrder.Clear()
			$coverChecked.Clear()
		}
	}
	return $path
}

# The players right now. If Windows' media controls stopped answering, ask for them again once;
# if that fails too, exit so the mod starts a fresh helper.
function Sessions {
	try {
		return , @($script:Manager.GetSessions())
	} catch {
		if ($null -eq $script:ManagerType) {
			throw
		}
	}
	try {
		$script:Manager = Await ($script:ManagerType::RequestAsync()) $script:ManagerType 10000
		return , @($script:Manager.GetSessions())
	} catch {
		FailCode 'stopped' (Why $_.Exception)
		exit 2
	}
}

function Poll {
	$script:coverBudgetMs = 1000
	$sessions = Sessions
	# Ask every player at once and wait for all of them together, so one slow player can't hold up the rest
	$tasks = New-Object 'System.Threading.Tasks.Task[]' $sessions.Count
	for ($i = 0; $i -lt $sessions.Count; $i++) {
		try {
			$tasks[$i] = Start-Op ($sessions[$i].TryGetMediaPropertiesAsync()) $PropertiesType
		} catch {
			# A player that went away while we asked
		}
	}
	$started = @($tasks | Where-Object { $null -ne $_ })
	if ($started.Count -gt 0) {
		try {
			$null = [System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$started, 1500)
		} catch {
			# Some failed; the ones that answered are still used below
		}
	}
	$items = New-Object System.Collections.Generic.List[string]
	$newGood = @{}
	$newStale = @{}
	for ($i = 0; $i -lt $sessions.Count; $i++) {
		$task = $tasks[$i]
		$key = ''
		try {
			$key = [string]$sessions[$i].SourceAppUserModelId + '#' + $i
		} catch {
			# A player that went away while we asked
		}
		if ($null -ne $task -and -not $task.IsCompleted) {
			# Still waiting for this player: show its last good reading, but for one poll only
			if ($key -and $lastGood.ContainsKey($key) -and -not $lastStale.ContainsKey($key)) {
				$items.Add([string]$lastGood[$key])
				$newGood[$key] = $lastGood[$key]
				$newStale[$key] = $true
			}
			continue
		}
		if ($null -eq $task -or [string]$task.Status -ne 'RanToCompletion') {
			continue
		}
		try {
			$session = $sessions[$i]
			$media = $task.Result
			if ($null -eq $media -or [string]::IsNullOrEmpty($media.Title)) {
				continue
			}
			$timeline = $session.GetTimelineProperties()
			$id = [string]$session.SourceAppUserModelId
			$cover = Cover $media ($id + '|' + $media.Artist + '|' + $media.Title)
			$info = $session.GetPlaybackInfo()
			$json = '{"index":' + $i +
				',"id":' + (J $id) +
				',"title":' + (J $media.Title) +
				',"artist":' + (J $media.Artist) +
				',"album":' + (J $media.AlbumTitle) +
				',"status":' + (J ([string]$info.PlaybackStatus)) +
				',"rate":' + (Rate $info) +
				',"positionMs":' + (Ms $timeline.Position) +
				',"startMs":' + (Ms $timeline.StartTime) +
				',"endMs":' + (Ms $timeline.EndTime) +
				',"updatedMs":' + (Unix $timeline.LastUpdatedTime) +
				',"cover":' + (J $cover) + '}'
			$items.Add($json)
			$newGood[$key] = $json
		} catch {
			# A player that closed while we asked, or one that doesn't fill everything in
		}
	}
	$script:lastGood = $newGood
	$script:lastStale = $newStale
	Reply ('"ok":true,"sessions":[' + ($items -join ',') + ']')
}

# The player at INDEX if it is still the same app; else that app's playing (or first) player; else nobody.
# Never another app: a pause or skip must not land on whatever else happens to be playing.
function Find([int]$index, [string]$id) {
	$sessions = Sessions
	if ($index -ge 0 -and $index -lt $sessions.Count -and $sessions[$index].SourceAppUserModelId -eq $id) {
		return $sessions[$index]
	}
	$same = @($sessions | Where-Object { $_.SourceAppUserModelId -eq $id })
	foreach ($s in $same) {
		if ([string]$s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') {
			return $s
		}
	}
	if ($same.Count -gt 0) {
		return $same[0]
	}
	return $null
}

function Control([string]$action, [int]$index, [string]$id, [long]$ms) {
	$session = Find $index $id
	if ($null -eq $session) {
		Fail 'That player is not there any more'
		return
	}
	if ($action -eq 'toggle') {
		$operation = $session.TryTogglePlayPauseAsync()
	} elseif ($action -eq 'next') {
		$operation = $session.TrySkipNextAsync()
	} elseif ($action -eq 'prev') {
		$operation = $session.TrySkipPreviousAsync()
	} else {
		$operation = $session.TryChangePlaybackPositionAsync($session.GetTimelineProperties().StartTime.Ticks + $ms * 10000)
	}
	$null = Await $operation ([bool])
	Reply '"ok":true'
}

if ($null -eq $Manager) {
	try {
		Add-Type -AssemblyName System.Runtime.WindowsRuntime
		$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
			$_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and $_.GetParameters().Count -eq 1 -and
			$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
		} | Select-Object -First 1
		$ManagerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
		$PropertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
		$StreamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage.Streams,ContentType=WindowsRuntime]
		# The first request after boot can be slow while Windows wakes its media service
		$Manager = Await ($ManagerType::RequestAsync()) $ManagerType 10000
	} catch {
		$stdout.WriteLine('{"ready":false,"code":"unavailable","detail":' + (J (Why $_.Exception)) + ',"error":' + (J ("unavailable: " + (Why $_.Exception))) + '}')
		exit 1
	}
}
$stdout.WriteLine('{"ready":true}')

while ($true) {
	$line = $stdin.ReadLine()
	if ($null -eq $line) {
		break # the game closed
	}
	$parts = $line.Split([char[]]' ', 3)
	if ($parts.Count -lt 2) {
		continue
	}
	try {
		$seq = [long]$parts[0]
	} catch {
		continue
	}
	try {
		$command = $parts[1]
		if ($command -eq 'poll') {
			Poll
		} elseif ($command -eq 'seek') {
			$rest = $parts[2].Split([char[]]' ', 3)
			Control 'seek' ([int]$rest[1]) $rest[2] ([long]$rest[0])
		} elseif ($command -in 'toggle', 'next', 'prev') {
			$rest = $parts[2].Split([char[]]' ', 2)
			Control $command ([int]$rest[0]) $rest[1] 0
		} else {
			Fail ('Unknown command ' + $command)
		}
	} catch {
		Fail (Why $_.Exception)
	}
}
