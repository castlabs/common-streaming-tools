# Common Streaming Tools

Creating CSF streams is a two stage process. The first stage createsCFF files as described in "Annex C.5 Streaming Delivery 
Target" of the CFF Media Format 1.2 Draft specification. These files are then processed with a second stage to be 
deliverable with a standard HTTP server. 

Please note that this tool doesn't encode video nor audio. It takes correctly encoded content and packages it appropriately.

The command line is always

```
java -jar common-streaming-tools-0.2.jar [command] [options] [arguments]
```


## Example Files

### Audio

* [AAC Stereo Track](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_audio.mp4)

### Video

* [Sintel 180p](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel_180p.mp4)
* [Sintel 270p](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel_270p.mp4)
* [Sintel 450p](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel_450p.mp4)
* [Sintel 540p](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel_540p.mp4)
* [Sintel 720p](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel_720p.mp4)
* [Sintel AAC Stereo - eng](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel_aac.mp4)
* [Sintel AAC Stereo - ger](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel_aac_ger.mp4)
* [Sintel DTSHD 5.1 - eng](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel.5.1.dts.dtshd)
* [Sintel DTSHD Stereo - eng](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/DASH_testfiles/Sintel.stereo.dts.dtshd)


## Create CSF/CFF file

The 'streaming-target' command creates CFF files. You should always package all files of an AdaptationSet with one run of
the tool. One run for all videos, but one run per audio-codec/language combination

Track IDs are guessed but can be overridden by the command line option `--track-id` or `-t`. All videos always get 
track ID 1, audio tracks get a trackId between 100 and 599 depending on the audio codec ( aac = 1xx, mlp = 2xx, ...). 
`xx` is replaced with two language dependent digits. 

As raw tracks do not contain any information about the language of the track. Set the track language by supplying the 
`--language` or `-l` command line option. If no language option is set "eng" is assumed. 

```
java -jar common-streaming-tools-0.3.jar streaming-target Sintel_180p.mp4 Sintel_270p.mp4 Sintel_450p.mp4 Sintel_540p.mp4 Sintel_720p.mp4 
java -jar common-streaming-tools-0.3.jar streaming-target Sintel_aac.mp4
java -jar common-streaming-tools-0.3.jar streaming-target Sintel_aac_ger.mp4 
java -jar common-streaming-tools-0.3.jar streaming-target Sintel.5.1.dts.dtshd Sintel.stereo.dts.dtshd
```

The result is always written into the current directory with appropriate extensions (uva, uvv, uvt). 

### Encryption
 
Common Encryption can be triggered by supplying the option `--content-encryption-key` for key (16 bytes as 32
character hex string) and `--uuid` for the key ID (usual UUID notation e.g. efe93ea0-142a-11e4-8c21-0800200c9a66). 
The second stage accepts encrypted and plain files. 


## Repackage CSF File for Streaming


### Most Simple Case: CSF file stays as is - Manifest uses media ranges

```
java -jar common-streaming-tools-0.3.jar create-simple-manifest Sintel_180p.uvv Sintel_270p.uvv Sintel_450p.uvv Sintel_540p.uvv Sintel_720p.uvv Sintel_aac.uva Sintel_aac_ger.uva Sintel.stereo.dts.uva Sintel.5.1.dts.uva
```

A `manifest.mpd` will be written into the current directory. 

