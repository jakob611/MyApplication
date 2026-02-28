import json
import re
import difflib
import os

# Paths
json_path = 'C:/Users/tomin/AndroidStudioProjects/MyApplication/app/src/main/assets/sets_reps.json'
video_list_path = 'C:/Users/tomin/Documents/naslovivideov.txt'

# Load sets_reps.json
with open(json_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

exercises = data['exercise_recommendations']

# Load video titles
video_urls = []
with open(video_list_path, 'r', encoding='utf-8') as f:
    video_urls = [line.strip() for line in f if line.strip()]

# Normalization function
def normalize(text):
    return re.sub(r'[^a-z0-9]', '', text.lower())

def clean_filename(url):
    filename = url.split('/')[-1]
    filename = filename.replace('.mp4', '')
    return filename

# Mapping
count_updated = 0
for exercise in exercises:
    name = exercise['name']
    norm_name = normalize(name)

    matches = []

    for url in video_urls:
        filename = clean_filename(url)
        norm_filename = normalize(filename)

        # Check if normalized exercise name is in normalized filename
        if norm_name in norm_filename:
            # Score based on how much of the filename is the exercise name
            # Shorter remaining filename is better (closer match)
            # Avoid matching "Squat" to "Squat Jump" if we have "Squat" video
            score = len(norm_name) / len(norm_filename)

            # Penalize if the filename seems substantially different
            matches.append((score, url))

    # Sort matches by score descending
    matches.sort(key=lambda x: x[0], reverse=True)

    if matches:
        # Prefer matches that don't have "Female" if possible, or maybe prefer Female?
        # The user has many _Female videos.
        # Let's simple take the highest score.
        exercise['video_url'] = matches[0][1]
        count_updated += 1
    else:
        # Try a more complex match?
        # e.g. "Dumbbell Bench Press" might be "Bench Press Dumbbell"
        pass

data['exercise_recommendations'] = exercises

with open(json_path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2)

print(f"Updated {count_updated} exercises with video URLs.")
