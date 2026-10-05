import {Config} from '@remotion/cli/config';

/**
 * Max-quality pipeline: lossless PNG frame intermediates, H.264 CRF 10
 * (visually lossless), x264 "slow" preset. Silent video - Douyin BGM is
 * added in-app by the creator.
 */
Config.setVideoImageFormat('png');
Config.setJpegQuality(100);
Config.setCodec('h264');
Config.setCrf(10);
Config.setX264Preset('slow');
Config.setOverwriteOutput(true);
Config.setConcurrency(2);
Config.setChromiumDisableWebSecurity(false);
