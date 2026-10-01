import 'dart:async';
import 'dart:convert';

import 'package:bot_toast/bot_toast.dart';
import 'package:flutter/material.dart';
import 'package:flutter_hbb/common/hbbs/hbbs.dart';
import 'package:flutter_hbb/models/ab_model.dart';
import 'package:get/get.dart';

import '../common.dart';
import '../utils/http_service.dart' as http;
import 'model.dart';
import 'platform_model.dart';
import 'native_model.dart';
import 'package:ntp/ntp.dart';
import 'package:http/http.dart' as time_http;


bool refreshingUser = false;

class ChinaNetworkTimeService {
  static DateTime? _lastNetworkTime;
  static DateTime? _lastUpdate;
  

  static final List<String> ntpServers = [
    'cn.pool.ntp.org',
    'ntp.ntsc.ac.cn',
    'time.edu.cn',
    'time.windows.com',
    'ntp1.aliyun.com',
    'ntp2.aliyun.com',
    'time1.cloud.tencent.com',
    'time2.cloud.tencent.com'
  ];
  

  static final List<String> httpTimeSources = [
    'https://www.baidu.com',
    'https://www.taobao.com',
    'https://www.qq.com',
    'https://www.jd.com',
    'https://www.163.com'
  ];
  

  static Future<DateTime?> _getNtpTime() async {
    for (var server in ntpServers) {
      try {
        final DateTime ntpTime = await NTP.now(
          lookUpAddress: server,
          timeout: Duration(seconds: 2)
        );
        return ntpTime;
      } catch (e) {
        print(' $e');
        continue;
      }
    }
    return null;
  }
  

  static Future<DateTime?> _getHttpTime() async {
    for (var url in httpTimeSources) {
      try {
        final response = await time_http.head(Uri.parse(url)).timeout(Duration(seconds: 3));
        
        if (response.headers.containsKey('date')) {
          final dateStr = response.headers['date']!;
          final dateTime = DateTime.parse(dateStr);
          return dateTime.toLocal();
        }
      } catch (e) {
        print(' $e');
        continue;
      }
    }
    return null;
  }
  

  static Future<DateTime> getTime() async {

    if (_lastNetworkTime != null && 
        _lastUpdate != null && 
        DateTime.now().difference(_lastUpdate!) < Duration(minutes: 5)) {
      final offset = DateTime.now().difference(_lastUpdate!);
      return _lastNetworkTime!.add(offset);
    }
    

    final ntpTime = await _getNtpTime();
    if (ntpTime != null) {
      _lastNetworkTime = ntpTime;
      _lastUpdate = DateTime.now();
      return ntpTime;
    }
    

    final httpTime = await _getHttpTime();
    if (httpTime != null) {
      _lastNetworkTime = httpTime;
      _lastUpdate = DateTime.now();
      return httpTime;
    }
    
    return DateTime.now();
  }
}

class UserModel {
  // final RxString emailName = ''.obs;
  final RxString userName = ''.obs;
  final RxBool isAdmin = false.obs;
  // final RxString userLogin = ''.obs;
  final RxString networkError = ''.obs;
  bool get isLogin => userName.isNotEmpty;
  WeakReference<FFI> parent;

  UserModel(this.parent) {
    userName.listen((p0) {
      // When user name becomes empty, show login button
      // When user name becomes non-empty:
      //  For _updateLocalUserInfo, network error will be set later
      //  For login success, should clear network error
      networkError.value = '';
    });
  // }
  }
  Future<void> clearExpiryInfo() async {
    await bind.mainSetLocalOption(key: 'user_email', value: '');
  }


  void refreshCurrentUser() async {
    //  return;
    if (bind.isDisableAccount()) return;
    networkError.value = '';
    final token = bind.mainGetLocalOption(key: 'access_token');
    if (token == '') {
      await updateOtherModels();
      return;
    }
    _updateLocalUserInfo();
    final url = await bind.mainGetApiServer();
    final body = {
      'id': await bind.mainGetMyId(),
      'uuid': await bind.mainGetUuid()
    };
    if (refreshingUser) return;
    try {
      refreshingUser = true;
      final http.Response response;
      try {
        response = await http.post(Uri.parse('$url/api/currentUser'),
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $token'
            },
            body: json.encode(body));
      } catch (e) {
        networkError.value = e.toString();
        rethrow;
      }
      refreshingUser = false;
      final status = response.statusCode;
      if (status == 401 || status == 400) {
        reset(resetOther: status == 401);
        return;
      }
      final data = json.decode(utf8.decode(response.bodyBytes));
      final error = data['error'];
      if (error != null) {
        throw error;
      }

      final user = UserPayload.fromJson(data);

      String? validationError = await validateUser(user);
      if (validationError != null) {
        await reset(resetOther: true);
        
        String errorMsg;
        switch (validationError) {
          case "account_expired":
            errorMsg = "账号过期了！";
            break;
          case "invalid_expiry_date":
            errorMsg = "授权日期错误！";
            break;
          case "device_uuid_mismatch":
            errorMsg = "识别码不一致！";
            break;
          default:
            errorMsg = "您输入的账号或密码不匹配！";
        }
        
        networkError.value = errorMsg;

        return;
      }

      _parseAndUpdateUser(user);
    } catch (e) {
      debugPrint('Failed to refreshCurrentUser: $e');
    } finally {
      refreshingUser = false;
      await updateOtherModels();
    }
  }

  static Map<String, dynamic>? getLocalUserInfo() {
    final userInfo = bind.mainGetLocalOption(key: 'user_info');
    if (userInfo == '') {
      return null;
    }
    try {
      return json.decode(userInfo);
    } catch (e) {
      debugPrint('Failed to get local user info "$userInfo": $e');
    }
    return null;
  }

  _updateLocalUserInfo() {
    final userInfo = getLocalUserInfo();
    if (userInfo != null) {
      userName.value = userInfo['name'];
    }
  }

  Future<void> reset({bool resetOther = false}) async {

    PlatformFFI.instance.setLoginUsername('');

    await bind.mainSetLocalOption(key: 'access_token', value: '');
    await bind.mainSetLocalOption(key: 'user_info', value: '');
    await bind.mainSetLocalOption(key: 'user_email', value: '');
    if (resetOther) {
      await gFFI.abModel.reset();
      await gFFI.groupModel.reset();
    }
    userName.value = '';
  }

  void parseAndUpdateUser(UserPayload user) {
    userName.value = user.name;
    isAdmin.value = user.isAdmin;
    if (user.name.isNotEmpty) {
      PlatformFFI.instance.setLoginUsername(user.name);
    }
    bind.mainSetLocalOption(key: 'user_email', value: user.email);
    bind.mainSetLocalOption(key: 'user_info', value: jsonEncode(user));
  }

  _parseAndUpdateUser(UserPayload user) {
    userName.value = user.name;
    isAdmin.value = user.isAdmin;
    if (user.name.isNotEmpty) {
      PlatformFFI.instance.setLoginUsername(user.name);
    }
    bind.mainSetLocalOption(key: 'user_email', value: user.email);
    bind.mainSetLocalOption(key: 'user_info', value: jsonEncode(user));
  }

  // update ab and group status
  static Future<void> updateOtherModels() async {
    await Future.wait([
      gFFI.abModel.pullAb(force: ForcePullAb.listAndCurrent, quiet: false),
      gFFI.groupModel.pull()
    ]);
  }

  Future<void> logOut({String? apiServer}) async {
    final tag = gFFI.dialogManager.showLoading(translate('Waiting'));
    try {
      final url = apiServer ?? await bind.mainGetApiServer();
      final authHeaders = getHttpHeaders();
      authHeaders['Content-Type'] = "application/json";
      await http
          .post(Uri.parse('$url/api/logout'),
              body: jsonEncode({
                'id': await bind.mainGetMyId(),
                'uuid': await bind.mainGetUuid(),
              }),
              headers: authHeaders)
          .timeout(Duration(seconds: 2));
    } catch (e) {
      debugPrint("request /api/logout failed: err=$e");
    } finally {
      await reset(resetOther: true);
      gFFI.dialogManager.dismissByTag(tag);
    }
  }

  Future<String?> validateUser(UserPayload user) async {

    if (user.isAdmin) {
      return null;
    }

    if (user.email.isNotEmpty) {
      String expiryStr;
      String? machineCode;

      final parts = user.email.split('@');
      if (parts.length > 1) {

        expiryStr = parts[0];
        machineCode = parts[1];
      } else {

        expiryStr = parts[0];
      }

      DateTime networkTime = await ChinaNetworkTimeService.getTime();

      try {
        DateTime expiryDate = DateTime(
          int.parse(expiryStr.substring(0, 4)),
          int.parse(expiryStr.substring(4, 6)),
          int.parse(expiryStr.substring(6, 8)),
          int.parse(expiryStr.substring(8, 10)),
          int.parse(expiryStr.substring(10, 12)),
        );

        if (expiryDate.isBefore(networkTime)) { 
          return "account_expired";
        }
      } catch (e) {
        return "invalid_expiry_date";
      }

      if (machineCode != null) {
        String currentUuid = await bind.mainGetUuid();
        if (machineCode != currentUuid) {
          return "device_uuid_mismatch";
        }
      }
    }

    return null; 
  }

  /// throw [RequestException]
  Future<LoginResponse> login(LoginRequest loginRequest) async {
    final url = await bind.mainGetApiServer();
    final resp = await http.post(Uri.parse('$url/api/login'),
        body: jsonEncode(loginRequest.toJson()));

    final Map<String, dynamic> body;
    try {
      body = jsonDecode(utf8.decode(resp.bodyBytes));
    } catch (e) {
      debugPrint("login: jsonDecode resp body failed: ${e.toString()}");
      if (resp.statusCode != 200) {
        BotToast.showText(
            contentColor: Colors.red, text: 'HTTP ${resp.statusCode}');
      }
      rethrow;
    }
    if (resp.statusCode != 200) {
      throw RequestException(resp.statusCode, body['error'] ?? '');
    }
    if (body['error'] != null) {
      throw RequestException(0, body['error']);
    }

    final loginResponse = getLoginResponseFromAuthBody(body);
    
    if (loginResponse.user != null) {
      String? validationError = await validateUser(loginResponse.user!);
      if (validationError != null) {
        throw RequestException(0, validationError);
      }
    }

    return loginResponse;
  }



  LoginResponse getLoginResponseFromAuthBody(Map<String, dynamic> body) {
    final LoginResponse loginResponse;
    try {
      loginResponse = LoginResponse.fromJson(body);
    } catch (e) {
      debugPrint("login: jsonDecode LoginResponse failed: ${e.toString()}");
      rethrow;
    }

    final isLogInDone = loginResponse.type == HttpType.kAuthResTypeToken &&
        loginResponse.access_token != null;
    if (isLogInDone && loginResponse.user != null) {
      _parseAndUpdateUser(loginResponse.user!);
    }

    return loginResponse;
  }

  static Future<List<dynamic>> queryOidcLoginOptions() async {
    try {
      final url = await bind.mainGetApiServer();
      if (url.trim().isEmpty) return [];
      final resp = await http.get(Uri.parse('$url/api/login-options'));
      final List<String> ops = [];
      for (final item in jsonDecode(resp.body)) {
        ops.add(item as String);
      }
      for (final item in ops) {
        if (item.startsWith('common-oidc/')) {
          return jsonDecode(item.substring('common-oidc/'.length));
        }
      }
      return ops
          .where((item) => item.startsWith('oidc/'))
          .map((item) => {'name': item.substring('oidc/'.length)})
          .toList();
    } catch (e) {
      debugPrint(
          "queryOidcLoginOptions: jsonDecode resp body failed: ${e.toString()}");
      return [];
    }
  }
}
