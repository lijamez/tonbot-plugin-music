# Tonbot Music Plugin [![Build Status](https://travis-ci.org/lijamez/tonbot-plugin-music.svg?branch=master)](https://travis-ci.org/lijamez/tonbot-plugin-music)

Turns [Tonbot](https://github.com/lijamez/Tonbot) into a full-featured music bot.

## Features

### Find tracks to play with YouTube searching
Find and play tracks directly from Tonbot.

```
t, music play sound of silence
```

### Play from your favorite sources
Just provide a direct link to a track, album, or playlist from these sources:
* YouTube
* Soundcloud
* Bandcamp
* Vimeo
* HTTP Links to audio files
* Discord File Upload
* ...and more

### Google Drive Support
Provide a link to a shared Google Drive track or folder to play it directly from Google Drive. Google Drive API key required.

### Spotify Support
Want to add a Spotify playlist? No problem. Provide a link to that playlist and Tonbot will load the tracks from Youtube.

### iTunes Playlist Upload
To "import" your songs from an iTunes playlist, create a playlist file by going to ``File > Library > Export Playlist``. Then, upload the file to Discord with the ``music play`` command without any arguments. Make sure your track metadata is correct, though. Tonbot will look up the tracks from Youtube.

### Play Modes

#### Round Robin Mode
It's really easy to add *a lot* of tracks into the queue but as a result, it can be difficult for other users to play what they want if there is already a large queue. The round robin mode solves this problem by playing back a track in the queue from each user, in a round robin fashion.

#### Shuffle
Shuffles the tracks in the queue.

### Repeat Modes
The following modes are supported:
* All
* Single

### Powerful Track Skipping
Choose to skip a single track:
```
t, music skip 2
```

a range of tracks:
```
t, music skip 5-12
```

all tracks:
```
t, music skip all
```

or just the ones you added:
```
t, music skip mine
```

### Track Seeking
Seek within the currently playing track.

Example:
```
t, music seek 1m30s
```

## Installation
Add ``net.tonbot.plugin.music.MusicPlugin`` to your Tonbot plugins config.

## Permissions
For this plugin to work correctly, Tonbot will need the following permissions:

Text Channel:
* Read Messages
* Send Messages
* Manage Messages
* Embed Links

Voice Channel:
* Connect
* Speak

## Configuration
The music plugin works right out of the box, but you can enable some extra features by providing API keys. The config file is located at ``{TONBOT CONFIG DIR}/plugin_config/net.tonbot.plugin.music.MusicPlugin.config``. 

### Enhanced Now Playing
The ``music nowplaying`` activity will show a description and thumbnail of the playing track. Fill out the ``youtubeApiKey`` field of the config.

To get an API key, visit the [Google APIs Console](https://code.google.com/apis/console/)

### Google Drive Support
The music plugin supports adding tracks from Google Drive via share links. You can give it a link to a track, or a folder of tracks and it will queue all of the tracks found inside. To set up Google Drive support, fill out the ``googleDriveApiKey`` field in the config. (It's possible that this key is the same as the ``youtubeApiKey``, depending on how you configured your API key.)

To get an API key, visit the [Google APIs Console](https://code.google.com/apis/console/)

### Spotify Support
Tonbot will look up track metadata using a Spotify track or album share link and then find the song on Youtube to play. To add support for Spotify, fil out the ``spotifyCredentials``.

To get Spotify credentials, go to the [My Applications Page](https://developer.spotify.com/my-applications) and then create an application. Take note of the Client ID and Client Secret.

## Acknowledgements
* Powered by [Lavaplayer](https://github.com/sedmelluq/lavaplayer)
