import sys

file_path = "shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt"

with open(file_path, "r") as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if "private suspend fun handlePlayFromMediaId(resolvedMediaId: String) {" in line:
        start_idx = i
    elif start_idx != -1 and "private suspend fun handlePlayFromSearch(query: String?, extras: Bundle?) {" in line:
        end_idx = i
        break

if start_idx == -1 or end_idx == -1:
    print("Could not find bounds")
    sys.exit(1)

# Extract original block
original_block = lines[start_idx:end_idx]

play_all_start = -1
playlist_start = -1
smart_playlist_start = -1
single_item_start = -1

for i, line in enumerate(original_block):
    if "if (resolvedMediaId.startsWith(ACTION_PLAY_ALL_PREFIX) ||" in line:
        play_all_start = i
    elif "if (resolvedMediaId.startsWith(PLAYLIST_PREFIX)) {" in line:
        playlist_start = i
    elif "if (resolvedMediaId.startsWith(SMART_PLAYLIST_PREFIX)) {" in line:
        smart_playlist_start = i
    elif "val fileInfo = mediaCacheService.cachedFiles.firstOrNull {" in line:
        single_item_start = i

play_all_lines = original_block[play_all_start:playlist_start]
playlist_lines = original_block[playlist_start:smart_playlist_start]
smart_playlist_lines = original_block[smart_playlist_start:single_item_start]
# Exclude the exact last `    }\n` from original_block which is the closing brace of handlePlayFromMediaId
single_item_lines = original_block[single_item_start:-2] # wait, single_item ends before the empty line or the closing brace.
# Let's inspect original_block's end
# It typically has:
#         savePlaybackSnapshot()
#     }
# \n
#     private suspend fun handlePlayFromSearch
if original_block[-1].strip() == "":
    single_item_lines = original_block[single_item_start:-2]
else:
    single_item_lines = original_block[single_item_start:-1]

def remove_outer_if_and_unindent(lines, is_play_all=False):
    if is_play_all:
        inner = lines[3:-2]
    else:
        inner = lines[1:-2]

    res = []
    for l in inner:
        if l.startswith("    "):
            res.append(l[4:])
        else:
            res.append(l)
    return res

new_play_all_body = remove_outer_if_and_unindent(play_all_lines, is_play_all=True)
new_playlist_body = remove_outer_if_and_unindent(playlist_lines, is_play_all=False)
new_smart_playlist_body = remove_outer_if_and_unindent(smart_playlist_lines, is_play_all=False)
new_single_item_body = single_item_lines

new_code = []
new_code.append("    private suspend fun handlePlayFromMediaId(resolvedMediaId: String) {\n")
new_code.append("        when {\n")
new_code.append("            resolvedMediaId.startsWith(ACTION_PLAY_ALL_PREFIX) ||\n")
new_code.append("            resolvedMediaId.startsWith(ACTION_SHUFFLE_PREFIX) -> {\n")
new_code.append("                handlePlayAllOrShuffle(resolvedMediaId)\n")
new_code.append("            }\n")
new_code.append("            resolvedMediaId.startsWith(PLAYLIST_PREFIX) -> {\n")
new_code.append("                handlePlayPlaylist(resolvedMediaId)\n")
new_code.append("            }\n")
new_code.append("            resolvedMediaId.startsWith(SMART_PLAYLIST_PREFIX) -> {\n")
new_code.append("                handlePlaySmartPlaylist(resolvedMediaId)\n")
new_code.append("            }\n")
new_code.append("            else -> {\n")
new_code.append("                handlePlaySingleItem(resolvedMediaId)\n")
new_code.append("            }\n")
new_code.append("        }\n")
new_code.append("    }\n")
new_code.append("\n")

new_code.append("    private suspend fun handlePlayAllOrShuffle(resolvedMediaId: String) {\n")
new_code.extend(new_play_all_body)
new_code.append("    }\n")
new_code.append("\n")

new_code.append("    private suspend fun handlePlayPlaylist(resolvedMediaId: String) {\n")
new_code.extend(new_playlist_body)
new_code.append("    }\n")
new_code.append("\n")

# Use suspend for all to preserve consistency and any potential async access safely without breaking signatures
new_code.append("    private suspend fun handlePlaySmartPlaylist(resolvedMediaId: String) {\n")
new_code.extend(new_smart_playlist_body)
new_code.append("    }\n")
new_code.append("\n")

new_code.append("    private suspend fun handlePlaySingleItem(resolvedMediaId: String) {\n")
new_code.extend(new_single_item_body)
new_code.append("    }\n\n")

lines[start_idx:end_idx] = new_code

with open(file_path, "w") as f:
    f.writelines(lines)
