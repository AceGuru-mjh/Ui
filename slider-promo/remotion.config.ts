import {Config} from '@remotion/cli/config';

Config.setVideoImageFormat('jpeg');
Config.setJpegQuality(92);
Config.setCodec('h264');
Config.setCrf(18);
Config.setAudioBitrate('256k');
Config.setOverwriteOutput(true);
Config.setConcurrency(2);
