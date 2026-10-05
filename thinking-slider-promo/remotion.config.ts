import {Config} from '@remotion/cli/config';

/**
 * Maximum-quality profile (user explicitly said file size does not matter):
 *  - PNG intermediate frames (no loss at capture)
 *  - H.264 CRF 8 - visually lossless for flat UI content. NOTE: Remotion
 *    rejects CRF 0 for H.264 because such streams cannot be played on
 *    iOS/macOS; CRF 8 is the practical near-lossless ceiling that stays
 *    universally playable (Douyin/CapCut/iOS/Android).
 *  - No audio track (silent promo; music is added on the Douyin side)
 */
Config.setVideoImageFormat('png');
Config.setCodec('h264');
Config.setCrf(8);
Config.setOverwriteOutput(true);
Config.setConcurrency(2);
