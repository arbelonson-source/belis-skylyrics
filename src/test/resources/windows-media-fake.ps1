# Runs windows-media.ps1 against a made-up media manager, so the helper can be tested with PowerShell
# on any system (WindowsMediaSourceTest). The players and songs here are invented.
param([string]$Script, [string]$CoverDir, [string]$Scenario = '', [string]$Flag = '', [string]$StartLog = '')

# First of all, a line in the test's start log: a start that never wrote it is PowerShell that stopped before running
# anything of ours (it has crashed so on a busy test machine), which the test tries again instead of failing
if ($StartLog) {
	[IO.File]::AppendAllText($StartLog, "started`n")
}

# A finished task, like a Windows operation that has already answered (spelled out for Windows PowerShell 5.1)
function global:Done($value) {
	$source = New-Object 'System.Threading.Tasks.TaskCompletionSource[object]'
	$source.SetResult($value)
	$source.Task
}

function global:FakeSession([string]$id, [string]$title, [string]$artist, [string]$status, [long]$positionMs, [long]$endMs) {
	$state = @{
		Title = $title; Artist = $artist; Status = $status; Position = $positionMs; End = $endMs
		Updated = [DateTimeOffset]::UtcNow; Thumbnail = $null; SlowAfter = 0; Calls = 0
	}
	$session = [pscustomobject]@{ SourceAppUserModelId = $id; State = $state }
	$session | Add-Member ScriptMethod TryGetMediaPropertiesAsync {
		$this.State.Calls = 1 + [int]$this.State.Calls
		# A player that answers the first time, then is too slow to answer (never, as far as a poll can tell)
		if ($this.State.SlowAfter -gt 0 -and $this.State.Calls -gt $this.State.SlowAfter) {
			$never = New-Object 'System.Threading.Tasks.TaskCompletionSource[object]'
			return $never.Task
		}
		Done ([pscustomobject]@{ Title = $this.State.Title; Artist = $this.State.Artist; AlbumTitle = 'Made Up Album'; Thumbnail = $this.State.Thumbnail })
	}
	$session | Add-Member ScriptMethod GetTimelineProperties {
		[pscustomobject]@{
			Position = [TimeSpan]::FromMilliseconds($this.State.Position)
			StartTime = [TimeSpan]::Zero
			EndTime = [TimeSpan]::FromMilliseconds($this.State.End)
			LastUpdatedTime = $this.State.Updated
		}
	}
	$session | Add-Member ScriptMethod GetPlaybackInfo {
		[pscustomobject]@{ PlaybackStatus = $this.State.Status }
	}
	$session | Add-Member ScriptMethod TryTogglePlayPauseAsync {
		$this.State.Status = if ($this.State.Status -eq 'Playing') { 'Paused' } else { 'Playing' }
		$this.State.Updated = [DateTimeOffset]::UtcNow
		Done $true
	}
	$session | Add-Member ScriptMethod TrySkipNextAsync {
		$this.State.Title = 'Song Two'
		$this.State.Position = 0
		$this.State.Updated = [DateTimeOffset]::UtcNow
		Done $true
	}
	$session | Add-Member ScriptMethod TrySkipPreviousAsync {
		Done $true
	}
	$session | Add-Member ScriptMethod TryChangePlaybackPositionAsync {
		param([long]$ticks)
		$this.State.Position = [long]($ticks / 10000)
		$this.State.Updated = [DateTimeOffset]::UtcNow
		Done $true
	}
	return $session
}

$broken = [pscustomobject]@{ SourceAppUserModelId = 'Broken.exe' }
$broken | Add-Member ScriptMethod TryGetMediaPropertiesAsync { throw 'this player went away' }

$sessions = @(
	(FakeSession 'Spotify.exe' 'Song One' 'Made Up Band' 'Playing' 61000 200000),
	(FakeSession 'MSEdge' 'Made Up Band - Video Song (Official Video)' 'Made Up Band' 'Paused' 5000 180000),
	# A second tab: browsers give every tab the same id, so controls must reach the right one
	(FakeSession 'MSEdge' 'Other Tab Song' 'Someone Else' 'Paused' 1000 120000),
	(FakeSession 'Silent.exe' '' '' 'Playing' 0 0),
	$broken
)

# A thumbnail the made-up player hands out: OpenReadAsync gives a plain .NET stream (the script accepts those)
function global:Thumb([scriptblock]$open) {
	$thumb = [pscustomobject]@{}
	$thumb | Add-Member ScriptMethod OpenReadAsync $open
	return $thumb
}

if ($Scenario -eq 'slowcovers') {
	# Three players whose covers never arrive: one never opens, two open but never finish copying
	$never = {
		$source = New-Object 'System.Threading.Tasks.TaskCompletionSource[object]'
		$source.Task
	}
	$hangs = {
		# Reading from a pipe nobody writes to waits forever
		Done (New-Object System.IO.Pipes.AnonymousPipeServerStream ([System.IO.Pipes.PipeDirection]::In))
	}
	$a = FakeSession 'Slow.A' 'Slow Song A' 'Made Up Band' 'Playing' 1000 200000
	$a.State.Thumbnail = Thumb $never
	$b = FakeSession 'Slow.B' 'Slow Song B' 'Made Up Band' 'Paused' 1000 200000
	$b.State.Thumbnail = Thumb $hangs
	$c = FakeSession 'Slow.C' 'Slow Song C' 'Made Up Band' 'Paused' 1000 200000
	$c.State.Thumbnail = Thumb $hangs
	$sessions = @($a, $b, $c)
} elseif ($Scenario -eq 'slowprops') {
	# Answers the first poll, then takes too long
	$a = FakeSession 'Slow.exe' 'Slow Props Song' 'Made Up Band' 'Playing' 1000 200000
	$a.State.SlowAfter = 1
	$sessions = @($a)
} elseif ($Scenario -eq 'newcover') {
	# The first read of the cover gets the previous song's picture; every later read gets the right one
	$a = FakeSession 'Cover.exe' 'Cover Song' 'Made Up Band' 'Playing' 1000 200000
	$a.State.Thumbnail = Thumb {
		$global:FakeOpens = 1 + [int]$global:FakeOpens
		$text = 'new cover'
		if ($global:FakeOpens -le 1) {
			$text = 'old cover'
		}
		Done (New-Object System.IO.MemoryStream (, [System.Text.Encoding]::ASCII.GetBytes($text)))
	}
	$sessions = @($a)
} elseif ($Scenario -eq 'vanish') {
	# The first player is hidden while the flag file exists, as if its app had closed
	$sessions = @(
		(FakeSession 'Followed.exe' 'Followed Song' 'Made Up Band' 'Playing' 1000 200000),
		(FakeSession 'Other.exe' 'Other Song' 'Someone Else' 'Paused' 2000 200000)
	)
}

$manager = [pscustomobject]@{ Sessions = $sessions; Flag = $Flag }
$manager | Add-Member ScriptMethod GetSessions {
	if ($this.Flag -and (Test-Path -LiteralPath $this.Flag)) {
		return @($this.Sessions | Select-Object -Skip 1)
	}
	return @($this.Sessions)
}
# Windows' own pick: the first player that is there. The helper must never fall back to it.
$manager | Add-Member ScriptMethod GetCurrentSession { @($this.GetSessions())[0] }

# Started the way the game starts it (WindowsMediaSource.command): read in and dot-sourced, so $script: means one scope
. ([scriptblock]::Create([IO.File]::ReadAllText($Script))) -CoverDir $CoverDir -Manager $manager
