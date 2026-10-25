Extra in-game pictures for the screenshots workflow. Each `*.sh` file here is sourced, in name order,
by `.github/scripts/screenshots.sh` after the standard pictures, with the game still running. Use the
helpers defined there: `hook "command" ...` (test hook commands, see DevTestHook), `shot <name>`, and
`sleep`. Put each feature's pictures in its own file so changes don't collide. Leave the game the way
you found it at the end (placement, layout, sky, time, GUI).
