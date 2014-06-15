require(["angular"], function(angular) {

  angular.module('myApp', []).
    factory('User', function($http, $rootScope) {

      var loginResult;

      var service = {};
      service.loggedIn = false;
      service.sobjects = [];
      service.webhooks = [];
      service.LOGIN_SUCCESS = "user:login:success";
      service.LOGIN_ERROR = "user:login:error";
      service.CREATE_WEBHOOK_SUCCESS = "user:create-webhook:success";
      service.CREATE_WEBHOOK_ERROR = "user:create-webhook:error";

      service.login = function(username, password) {
        $http.post('/login', {username: username, password: password}).
          success(function(data) {
            service.loggedIn = true;
            loginResult = data;
            service.fetchSobjects();
            service.fetchWebhooks();
          }).
          error(function(data) {
            $rootScope.$broadcast(service.LOGIN_ERROR, data);
          });
      };

      service.httpConfig = function() {
        var config = {};
        config.headers = {
          "X-SESSION-ID": loginResult.sessionId,
          "X-SERVER-URL": loginResult.serverUrl,
          "X-METADATA-SERVER-URL": loginResult.metadataServerUrl
        };
        return config;
      };

      service.fetchSobjects = function() {
        $http.get('/sobjects', service.httpConfig()).
          success(function(data) {
            service.sobjects = data;
          }).
          error(function(data) {
            console.error(data);
          });
      };

      service.fetchWebhooks = function() {
        $http.get('/webhooks', service.httpConfig()).
          success(function(data) {
            service.webhooks = data;
          }).
          error(function(data) {
            console.error(data);
          });
      };

      service.createWebhook = function(name, sobject, events, url) {
        var data = {
          name: name,
          sobject: sobject,
          events: events,
          url: url
        };

        $http.post('/webhooks', data, service.httpConfig()).
          success(function(data) {
            service.fetchWebhooks();
            $rootScope.$broadcast(service.CREATE_WEBHOOK_SUCCESS);
          }).
          error(function(data) {
            $rootScope.$broadcast(service.CREATE_WEBHOOK_ERROR, data);
          });
      };

      return service;
    }).
    controller('Login', function($scope, User) {
      $scope.User = User;

      $scope.loggingIn = false;

      $scope.$on(User.LOGIN_ERROR, function(event, data) {
        $scope.loggingIn = false;
        $scope.errorMessage = data.error.message;
      });

      $scope.login = function() {
        $scope.loggingIn = true;
        $scope.errorMessage = "";
        User.login($scope.username, $scope.password);
      };

    }).
    controller('Webhooks', function($scope, $http, User) {
      $scope.User = User;

      $scope.events = {};

      $scope.$on(User.CREATE_WEBHOOK_ERROR, function(event, data) {
        $scope.errorMessage = data.error.message;
      });

      $scope.$on(User.CREATE_WEBHOOK_SUCCESS, function(event, data) {
        $scope.errorMessage = "";
        $scope.name = "";
        $scope.sobject = null;
        $scope.events = {};
        $scope.url = "";
      });

      $scope.createWebhook = function() {
        var selectedEvents = [];
        for (var event in $scope.events) {
          if($scope.events.hasOwnProperty(event)) {
            if ($scope.events[event]) {
              selectedEvents.push(event);
            }
          }
        }
        User.createWebhook($scope.name, $scope.sobject, selectedEvents, $scope.url);
      };
    });

});