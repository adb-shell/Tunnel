import 'package:get/get.dart';

final developerLoginBypassEnabled = false.obs;

void enableDeveloperLoginBypass() {
  developerLoginBypassEnabled.value = true;
}
