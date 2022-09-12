import 'dart:io';

import 'package:dji/dji.dart';
import 'package:dji/flight.dart';
import 'package:dji/messages.dart';
import 'package:ffmpeg_kit_flutter_full_gpl/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_full_gpl/ffmpeg_kit_config.dart';
import 'package:flutter/material.dart';

import 'package:flutter/services.dart';

import 'package:local_assets_server/local_assets_server.dart';
import 'dart:developer' as developer;

import 'package:path_provider/path_provider.dart';

class DroneControllerView extends StatefulWidget {
  const DroneControllerView();

  static const String routeName = "/droneControllerView";

  @override
  State<DroneControllerView> createState() => _DroneControllerViewState();
}

class _DroneControllerViewState extends State<DroneControllerView>
    implements DjiFlutterApi {
  // Drone parameters
  String _platformVersion = 'Unknown';
  String _droneStatus = 'Connecting';
  String _droneBatteryPercent = '0';
  String _droneAltitude = '0.0';
  String _droneLatitude = '0.0';
  String _droneLongitude = '0.0';
  String _droneSpeed = '0.0';
  String _droneRoll = '0.0';
  String _dronePitch = '0.0';
  String _droneYaw = '0.0';

  // Video parameters
  IOSink? _videoFeedSink;
  File? _videoFeedFile;
  final String _outputFileName = 'output.m3u8';
  String? _localServerUrl;
  // VlcPlayerController? _vlcController;
  int? _ffmpegKitSessionId;

  @override
  void initState() {
    super.initState();
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeRight,
      // DeviceOrientation.landscapeLeft,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersive);

    DjiFlutterApi.setup(this);
    _getPlatformVersion();
    Future.delayed(Duration(milliseconds: 100))
        .then((value) => connectToDrone().then((value) => _startVideoFeed()));
  }

  Future<void> _takeOff() async {
    try {
      developer.log(
        'Takeoff requested',
        name: kLogKindDjiFlutterPlugin,
      );
      await Dji.takeOff();
    } on PlatformException catch (e) {
      developer.log(
        'Takeoff PlatformException Error',
        error: e,
        name: kLogKindDjiFlutterPlugin,
      );
    } catch (e) {
      developer.log(
        'Takeoff Error',
        error: e,
        name: kLogKindDjiFlutterPlugin,
      );
    }
  }

  Future<void> _land() async {
    try {
      developer.log(
        'Land requested',
        name: kLogKindDjiFlutterPlugin,
      );
      await Dji.land();
    } on PlatformException catch (e) {
      developer.log(
        'Land PlatformException Error',
        error: e,
        name: kLogKindDjiFlutterPlugin,
      );
    } catch (e) {
      developer.log(
        'Land Error',
        error: e,
        name: kLogKindDjiFlutterPlugin,
      );
    }
  }

  @override
  void sendVideo(Stream stream) {
    if (stream.data != null && _videoFeedFile != null) {
      developer.log("sendVideo stream data received: ${stream.data?.length}",
          name: kLogKindDjiFlutterPlugin);
      return;
    }

    try {
      _videoFeedSink?.add(stream.data!);
      developer.log("Received ${stream.data!.lengthInBytes} bytes");
    } catch (e) {
      developer.log(
        'sendVideo videoFeedSink Error',
        error: e,
        name: kLogKindDjiFlutterPlugin,
      );
    }
  }

  Future<void> _startVideoFeed() async {
    await Dji.videoFeedStart();
  }

  Future<void> _startVideoFeed2() async {
    try {
      developer.log("Video Feed start requested",
          name: kLogKindDjiFlutterPlugin);

      final String? inputPipe = await FFmpegKitConfig.registerNewFFmpegPipe();
      if (inputPipe == null) {
        developer.log("Video Feed start failed - no Input Pipe",
            name: kLogKindDjiFlutterPlugin);
        return;
      }

      _videoFeedFile = File(inputPipe);
      final String? outputPipe = await FFmpegKitConfig.registerNewFFmpegPipe();
      if (outputPipe == null) {
        developer.log("Video Feed start failed - no Output Pipe",
            name: kLogKindDjiFlutterPlugin);
        return;
      }

      // We must close the output pipe here, otherwise the FFMPEG convertion won't start.
      FFmpegKitConfig.closeFFmpegPipe(outputPipe);
      _videoFeedSink = _videoFeedFile?.openWrite();

      final Directory dir = await getTemporaryDirectory();
      final String outputPath = "${dir.path}/$_outputFileName";
      final File outputFile = File(outputPath);

      if (await outputFile.exists()) {
        outputFile.delete();
      }

      if (_localServerUrl == null) {
        // Start local server

        final server = LocalAssetsServer(
            address: InternetAddress.loopbackIPv4,
            assetsBasePath: '',
            rootDir: Directory(dir.path),
            port: 8080,
            logger: const DebugLogger());

        await server.serve();
        _localServerUrl = "http://${server.address.address}:${server.port}";
        developer.log("Server Address $_localServerUrl",
            name: kLogKindDjiFlutterPlugin);
      }

      // Initialize the VLC Video Player

      // setState(() {
      //   _vlcController ??= VlcPlayerController.network(
      //     _localServerUrl!,
      //     options: VlcPlayerOptions(
      //       video: VlcVideoOptions(
      //         [
      //           VlcVideoOptions.dropLateFrames(true),
      //           VlcVideoOptions.skipFrames(true),
      //         ],
      //       ),
      //       advanced: VlcAdvancedOptions([
      //         VlcAdvancedOptions.fileCaching(0),
      //         VlcAdvancedOptions.networkCaching(0),
      //         VlcAdvancedOptions.liveCaching(0),
      //         VlcAdvancedOptions.clockSynchronization(0),
      //       ]),
      //       sout: VlcStreamOutputOptions([
      //         VlcStreamOutputOptions.soutMuxCaching(0),
      //       ]),
      //       extras: [],
      //     ),
      //   );
      // });

      // _vlcController?.addOnInitListener(() async {
      //   developer.log(
      //     'VLC Player: addOnInitListener - initialized',
      //     name: kLogKindDjiFlutterPlugin,
      //   );
      // });

      await Dji.videoFeedStart();
      developer.log("Video Feed Started", name: kLogKindDjiFlutterPlugin);

      bool playing = false;
      const hlsTimeDurationInMs = 1000; // HTTP Live Streaming duration

      await FFmpegKit.executeAsync(
          '-y -probesize 32 -analyzeduration 0 -f rawvideo -video_size 1280x720 -pix_fmt yuv420p -i $inputPipe -c:v libx264 -preset ultrafast -tune zerolatency -filter:v "setpts=0.8*PTS" -f hls -hls_time ${hlsTimeDurationInMs}ms -hls_flags split_by_time+delete_segments -an $outputPath',
          (session) async {
            _ffmpegKitSessionId = session.getSessionId();

            developer.log(
              'FFmpegKit sessionId: $_ffmpegKitSessionId',
              name: kLogKindDjiFlutterPlugin,
            );
          },
          (log) {},
          (statistics) async {
            if (statistics.getTime() > hlsTimeDurationInMs &&
                playing == false) {
              playing = true;

              // We must add another second (although the reason for this is unknown, as in Debug mode the additional 1s is not necessary, but in release-mode it is...)
              await Future.delayed(
                const Duration(milliseconds: 1000),
              );
            }
          });
    } on PlatformException catch (e) {
      developer.log(
        'Video Feed Start PlatformException Error',
        error: e,
        name: kLogKindDjiFlutterPlugin,
      );
    } catch (e) {
      developer.log(
        'Video Feed Start Error',
        error: e,
        name: kLogKindDjiFlutterPlugin,
      );
    }
  }

  @override
  void setStatus(Drone drone) {
    setState(() {
      _droneStatus = drone.status ?? "Disconnected";
      _droneStatus = _droneStatus == "Registered"
          ? "Controller not connected"
          : _droneStatus;
      _droneStatus = _droneStatus == "Delegated" ? "Connected" : _droneStatus;
      _droneAltitude = drone.altitude?.toStringAsFixed(2) ?? "-";
      _droneBatteryPercent = drone.batteryPercent?.toStringAsFixed(0) ?? "-";
      _droneLatitude = drone.latitude?.toStringAsFixed(7) ?? "-";
      _droneLongitude = drone.longitude?.toStringAsFixed(7) ?? "-";
      _droneSpeed = drone.speed?.toStringAsFixed(0) ?? "-";
      _droneRoll = drone.roll?.toStringAsFixed(0) ?? "-";
      _dronePitch = drone.pitch?.toStringAsFixed(0) ?? "-";
      _droneYaw = drone.yaw?.toStringAsFixed(0) ?? "-";
    });
  }

  Future<bool> connectToDrone() async {
    developer.log("connectDrone requested", name: kLogKindDjiFlutterPlugin);

    try {
      await Dji.connectDrone();
      await _delegateDrone();

      return true;
    } on PlatformException catch (e) {
      developer.log(
        'connectDrone PlatformException Error',
        error: e,
        name: kLogKindDjiFlutterPlugin,
      );
    } catch (e) {
      developer.log("connectDrone Error",
          error: e, name: kLogKindDjiFlutterPlugin);
    }

    return false;
  }

  Future<void> _getPlatformVersion() async {
    String platformVersion;

    try {
      platformVersion = await Dji.platformVersion ?? "Unknown platform version";
    } on PlatformException {
      platformVersion = "Failed to get platform version";
    }

    // Make sure we don't setState if the widget was removed before the asynchronous platformversion message was received
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  Future<void> _delegateDrone() async {
    developer.log("delegateDrone requested", name: kLogKindDjiFlutterPlugin);
    try {
      await Dji.delegateDrone();
    } on PlatformException catch (e) {
      developer.log("delegateDrone PlatformException Error",
          error: e, name: kLogKindDjiFlutterPlugin);
    } catch (e) {
      developer.log("delegateDrone Error",
          error: e, name: kLogKindDjiFlutterPlugin);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
        height: MediaQuery.of(context).size.height,
        width: MediaQuery.of(context).size.width,
        child: DefaultTextStyle(
          style: const TextStyle(color: Colors.white, fontSize: 18),
          child: SafeArea(
            top: false,
            child: Stack(
              children: [
                Container(
                    height: MediaQuery.of(context).size.height,
                    width: MediaQuery.of(context).size.width,
                    // _vlcController != null
                    //     ? VlcPlayer(
                    //         controller: _vlcController!, aspectRatio: 16 / 9)
                    //     :
                    child: Container()),
                Positioned(
                  top: 10,
                  child: Container(
                    width: MediaQuery.of(context).size.width,
                    height: MediaQuery.of(context).size.height,
                    padding: const EdgeInsets.symmetric(
                        horizontal: 15, vertical: 10),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text("Status: $_droneStatus"),
                            Text("🔋 $_droneBatteryPercent%")
                          ],
                        ),
                        const SizedBox(
                          height: 40,
                          width: 100,
                        )
                      ],
                    ),
                  ),
                ),
                Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: SizedBox(
                      height: 120,
                      width: MediaQuery.of(context).size.width,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Material(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                IconButton(
                                  onPressed: () async {
                                    await _takeOff();
                                  },
                                  icon: const Icon(Icons.flight_takeoff_sharp),
                                ),
                                Container(
                                  color: Colors.black,
                                  width: 50,
                                  height: 20,
                                ),
                                IconButton(
                                  onPressed: () async {
                                    await _land();
                                  },
                                  icon: const Icon(Icons.flight_land_sharp),
                                ),
                              ],
                            ),
                          ),
                          Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Container(
                                height: 40,
                                width: 40,
                                decoration: BoxDecoration(
                                    color: Colors.red,
                                    borderRadius: BorderRadius.circular(40)),
                              )
                            ],
                          )
                        ],
                      ),
                    ),
                  ),
                ),
                Positioned(
                    bottom: 20,
                    child: SizedBox(
                      width: MediaQuery.of(context).size.width,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                              "Speed: $_droneSpeed\nLat: $_droneLatitude\nLong: $_droneLongitude"),
                          const SizedBox(
                            width: 40,
                          ),
                          Text(
                              "Roll: $_droneRoll\nPitch: $_dronePitch\nYaw: $_droneYaw")
                        ],
                      ),
                    )),
              ],
            ),
          ),
        ));
  }
}
