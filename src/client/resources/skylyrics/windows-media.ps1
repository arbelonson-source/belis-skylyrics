# Beli's SkyLyrics: Windows media helper.
#
# The mod starts this hidden, for as long as the game runs, to read what Windows' media controls
# are playing (the panel that pops up when you press a media or volume key). Spotify, browsers,
# Media Player, Apple Music and most other players report to them.
#
# The mod writes one numbered command per line. Each one gets exactly one line of JSON back,
# carrying the same number:
#   7 poll           -> {"seq":7,"ok":true,"sessions":[{...}, ...]}
#   8 toggle ID      -> {"seq":8,"ok":true}       (also: next ID, prev ID)
#   9 seek MS ID     -> {"seq":9,"ok":true}
# A problem answers {"seq":N,"ok":false,"error":"..."}. When the game closes our input, we stop.
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
	$stdout.WriteLine('{"ready":false,"error":"System.Web is missing from Windows"}')
	exit 1
}

$seq = 0
# Filled in when we talk to the real Windows: a helper to wait on its operations, and their result types
$asTask = $null
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

# Waits for a Windows operation (or, in the tests, a .NET task) and returns its result
function Await($operation, [Type]$resultType) {
	if ($operation -is [System.Threading.Tasks.Task]) {
		$task = $operation
	} else {
		$task = $script:asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
	}
	if (-not $task.Wait(1500)) {
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

# Covers are saved as files the mod can open. Song key -> file ('' = none could be read)
$covers = @{}
$coverOrder = New-Object System.Collections.Generic.Queue[string]
$coverCount = 0

function Cover($media, [string]$key) {
	if ($covers.ContainsKey($key)) {
		return $covers[$key]
	}
	if ($null -eq $media.Thumbnail -or -not $CoverDir) {
		return '' # not remembered: browsers often add the cover a moment after the title
	}
	$path = ''
	try {
		$stream = Await ($media.Thumbnail.OpenReadAsync()) $StreamType
		$script:coverCount++
		$path = Join-Path $CoverDir ('cover-' + $PID + '-' + $script:coverCount + '.img')
		$source = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
		$file = [System.IO.File]::Create($path)
		try {
			$source.CopyTo($file)
		} finally {
			$file.Dispose()
			$source.Dispose()
		}
	} catch {
		$path = ''
	}
	if ($covers.Count -gt 200) {
		$covers.Clear() # only failed lookups pile up; the files themselves are capped below
	}
	$covers[$key] = $path
	if ($path) {
		$coverOrder.Enqueue($key)
		while ($coverOrder.Count -gt 6) {
			$old = $coverOrder.Dequeue()
			if ($covers[$old]) {
				Remove-Item -LiteralPath $covers[$old] -ErrorAction SilentlyContinue
			}
			$covers.Remove($old)
		}
	}
	return $path
}

function Poll {
	$items = New-Object System.Collections.Generic.List[string]
	foreach ($session in $Manager.GetSessions()) {
		try {
			$media = Await ($session.TryGetMediaPropertiesAsync()) $PropertiesType
			if ($null -eq $media -or [string]::IsNullOrEmpty($media.Title)) {
				continue
			}
			$timeline = $session.GetTimelineProperties()
			$id = [string]$session.SourceAppUserModelId
			$cover = Cover $media ($id + '|' + $media.Artist + '|' + $media.Title)
			$json = '{"id":' + (J $id) +
				',"title":' + (J $media.Title) +
				',"artist":' + (J $media.Artist) +
				',"album":' + (J $media.AlbumTitle) +
				',"status":' + (J ([string]$session.GetPlaybackInfo().PlaybackStatus)) +
				',"positionMs":' + (Ms $timeline.Position) +
				',"startMs":' + (Ms $timeline.StartTime) +
				',"endMs":' + (Ms $timeline.EndTime) +
				',"updatedMs":' + (Unix $timeline.LastUpdatedTime) +
				',"cover":' + (J $cover) + '}'
			$items.Add($json)
		} catch {
			# A player that closed while we asked, or one that doesn't fill everything in
		}
	}
	Reply ('"ok":true,"sessions":[' + ($items -join ',') + ']')
}

function Control([string]$action, [string]$id, [long]$ms) {
	$session = $null
	foreach ($s in $Manager.GetSessions()) {
		if ($s.SourceAppUserModelId -eq $id) {
			$session = $s
		}
	}
	if ($null -eq $session) {
		$session = $Manager.GetCurrentSession()
	}
	if ($null -eq $session) {
		Fail 'Nothing is playing'
		return
	}
	$operation = switch ($action) {
		'toggle' { $session.TryTogglePlayPauseAsync() }
		'next' { $session.TrySkipNextAsync() }
		'prev' { $session.TrySkipPreviousAsync() }
		'seek' { $session.TryChangePlaybackPositionAsync($session.GetTimelineProperties().StartTime.Ticks + $ms * 10000) }
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
		$Manager = Await ($ManagerType::RequestAsync()) $ManagerType
	} catch {
		$stdout.WriteLine('{"ready":false,"error":' + (J ("Windows' media controls aren't available: " + $_.Exception.Message)) + '}')
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
		switch ($parts[1]) {
			'poll' { Poll }
			'seek' {
				$args2 = $parts[2].Split([char[]]' ', 2)
				Control 'seek' $args2[1] ([long]$args2[0])
			}
			{ $_ -in 'toggle', 'next', 'prev' } { Control $parts[1] $parts[2] 0 }
			default { Fail ('Unknown command ' + $parts[1]) }
		}
	} catch {
		Fail $_.Exception.Message
	}
}
