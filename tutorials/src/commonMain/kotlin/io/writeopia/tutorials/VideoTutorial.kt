package io.writeopia.tutorials

internal fun videoTutorial() =
"""
{
    "id": "VideoTutorial1",
    "title": "Video Tutorial",
    "workspaceId": "disconnected_user",
    "content": [
        {
            "id": "VideoTitleId1",
            "type": {
                "name": "title",
                "number": 11
            },
            "text": "Video Tutorial",
            "spans": [
                {
                    "start": 0,
                    "end": 0,
                    "span": "BOLD"
                }
            ],
            "decoration": {
                "backgroundColor": -7829368
            },
            "position": 0
        },
        {
            "id": "VideoContentId1",
            "type": {
                "name": "video",
                "number": 6
            },
            "url": "https://storage.googleapis.com/writeopia-resources/generate-section.mov",
            "position": 1
        },
        {
            "id": "VideoSpaceId1",
            "type": {
                "name": "message",
                "number": 0
            },
            "text": "",
            "position": 2
        }
    ],
    "createdAt": 1739622940593,
    "lastUpdatedAt": 1739622940593,
    "parentId": "root",
    "isLocked": false,
    "icon": {
        "label": "play_arrow",
        "tint": -65281
    }
}
"""
