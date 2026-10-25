# Runs windows-media.ps1 against a made-up media manager, so the helper can be tested with PowerShell
# on any system (WindowsMediaSourceTest). The players and songs here are invented.
param([string]$Script, [string]$CoverDir)

# A finished task, like a Windows operation that has already answered (spelled out for Windows PowerShell 5.1)
function global:Done($value) {
	$source = New-Object 'System.Threading.Tasks.TaskCompletionSource[object]'
	$source.SetResult($value)
	$source.Task
}

function global:FakeSession([string]$id, [string]$title, [string]$artist, [string]$status, [long]$positionMs, [long]$endMs) {
	$state = @{
		Title = $title; Artist = $artist; Status = $status; Position = $positionMs; End = $endMs
		Updated = [DateTimeOffset]::UtcNow
	}
	$session = [pscustomobject]@{ SourceAppUserModelId = $id; State = $state }
	$session | Add-Member ScriptMethod TryGetMediaPropertiesAsync {
		Done ([pscustomobject]@{ Title = $this.State.Title; Artist = $this.State.Artist; AlbumTitle = 'Made Up Album'; Thumbnail = $null })
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
$manager = [pscustomobject]@{ Sessions = $sessions }
$manager | Add-Member ScriptMethod GetSessions { $this.Sessions }
$manager | Add-Member ScriptMethod GetCurrentSession { $this.Sessions[0] }

# Started the way the game starts it (WindowsMediaSource.command): read in and dot-sourced, so $script: means one scope
. ([scriptblock]::Create([IO.File]::ReadAllText($Script))) -CoverDir $CoverDir -Manager $manager
