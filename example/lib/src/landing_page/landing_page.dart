import 'package:dji/dji.dart';
import 'package:dji/flight.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/src/widgets/container.dart';
import 'package:flutter/src/widgets/framework.dart';
import 'dart:developer' as developer;

class LandingPage extends StatelessWidget {
  const LandingPage();

  static const String routeName = "/landingPage";

  @override
  void initState() {
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  }

  @override
  void didUpdateWidget(Type oldWidget) {
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
          bottom: false,
          child: Center(
            child: Padding(
              padding: const EdgeInsets.only(top: 40.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  const Text(
                    "ODaT",
                    style: TextStyle(color: Color(0xff64AC9B), fontSize: 40),
                  ),
                  const SizedBox(
                    height: 400,
                  ),
                  ElevatedButton(
                      style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xff64AC9B)),
                      onPressed: () async {
                        developer.log("Register app requested",
                            name: kLogKindDjiFlutterPlugin);

                        try {
                          await Dji.registerApp();
                          // ignore: use_build_context_synchronously
                          Navigator.pushNamed(context, "/droneControllerView")
                              .then((value) =>
                                  SystemChrome.setPreferredOrientations(
                                      [DeviceOrientation.portraitUp]));
                        } on PlatformException catch (e) {
                          developer.log("Register App PlatformException Error",
                              error: e, name: kLogKindDjiFlutterPlugin);
                        } catch (e) {
                          developer.log("Register App Error",
                              error: e, name: kLogKindDjiFlutterPlugin);
                        }
                      },
                      child: const Text(
                        "Connect to drone",
                        style: TextStyle(color: Colors.black),
                      )),
                  ElevatedButton(
                      style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xff64AC9B)),
                      onPressed: () async {
                        developer.log("Register app requested",
                            name: kLogKindDjiFlutterPlugin);

                        Navigator.pushNamed(context, "/example");
                      },
                      child: const Text(
                        "Go to example",
                        style: TextStyle(color: Colors.black),
                      )),
                  ElevatedButton(
                      onPressed: () async {
                        await Dji.disconnectDrone();
                      },
                      child: const Text("Disconnect drone",
                          style: TextStyle(color: Colors.black)))
                ],
              ),
            ),
          )),
    );
  }
}
