import 'package:dji_example/src/drone_controller_view/drone_controller_view.dart';
import 'package:dji_example/src/landing_page/landing_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'example/example.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  MyAppState createState() => MyAppState();
}

class MyAppState extends State<MyApp> {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(onGenerateRoute: (RouteSettings routeSettings) {
      return MaterialPageRoute<void>(
        settings: routeSettings,
        builder: (BuildContext context) {
          switch (routeSettings.name) {
            case ExampleWidget.routeName:
              return const ExampleWidget();
            case DroneControllerView.routeName:
              return const DroneControllerView();
            case LandingPage.routeName:
            default:
              return const LandingPage();
          }
          // return const LandingPage();
        },
      );
    });
  }
}
