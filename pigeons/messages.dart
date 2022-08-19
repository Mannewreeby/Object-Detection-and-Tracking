import 'package:pigeon/pigeon.dart';

class Version {
  String? string;
}

class Battery {
  int? level;
}

class Drone {
  String? status;
  double? batteryPercent;
  double? altitude;
  double? latitude;
  double? longitude;
  double? speed;
  double? roll;
  double? pitch;
  double? yaw;
}

class Media {
  String? fileName;
  String? fileUrl;
  int? fileIndex;
}

class Stream {
  Uint8List? data;
}

@HostApi()
abstract class DjiHostApi {
  Version getPlatformVersion();
  Battery getBatteryLevel();
  void registerApp();
  void connectDrone();
  void disconnectDrone();
  void delegateDrone();
  void takeOff();
  void land();
  void start(String flightJson);
  void mobileRemoteController(
    bool enabled,
    double leftStickHorizontal,
    double leftStickVertical,
    double rightStickHorizontal,
    double rightStickVertical,
  );
  void virtualStick(
    bool enabled,
    double pitch,
    double roll,
    double yaw,
    double verticalThrottle,
  );
  void gimbalRotatePitch(
    double degrees,
  );
  List<Media> getMediaList();
  String downloadMedia(int fileIndex);
  bool deleteMedia(int fileIndex);
  void videoFeedStart();
  void videoFeedStop();
  // void videoRecordStart();
  // void videoRecordStop();
}

@FlutterApi()
abstract class DjiFlutterApi {
  void setStatus(Drone drone);
  void sendVideo(Stream stream);
}
