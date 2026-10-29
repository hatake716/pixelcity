# Campaign completion fixtures

Generated on 2026-09-12 by CampaignTest with:

```
./gradlew testDebugUnitTest --tests '*CampaignTest' -Ppixelcity.preview=<output-directory>
```

The initial endgame fixtures seed mature tiles and capital for deterministic validation.
After setup, each chapter, all policy choices and the final six consecutive qualifying months
are processed through the real City simulation. The exported saves retain the player's actual
buildings, policies, economy, certification and the pending ending dialogue.

CampaignFlowTest opens these saves to verify the three endings and continued play in the same city.
These files belong to the test APK only. They are not templates or free cities granted in the game.
