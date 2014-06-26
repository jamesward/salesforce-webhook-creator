require(["angular"], function(angular) {

  angular.module('myApp', []).
    controller('WebhooksController', function($scope, $http) {

      var fetchWebhooks = function() {
        $http.get('/webhooks').
          success(function(data) {
            $scope.webhooks = data;
          }).
          error(function(data) {
            console.error(data);
          });
      };

      var createWebhook = function(name, sobject, events, url) {
        var data = {
          name: name,
          sobject: sobject,
          events: events,
          url: url
        };

        $http.post('/webhooks', data).
          success(function(data) {
            initForm();
            fetchWebhooks();
          }).
          error(function(data) {
            $scope.errorMessage = data.error.message;
          });
      };

      var fetchSobjects = function() {
        $http.get('/sobjects').
          success(function(data) {
            $scope.sobjects = data;
          }).
          error(function(data) {
            console.error(data);
          });
      };

      var initForm = function() {
        $scope.errorMessage = "";
        $scope.name = "";
        $scope.sobject = null;
        $scope.events = {};
        $scope.url = "";
      };

      fetchWebhooks();
      fetchSobjects();
      initForm();

      $scope.createWebhook = function() {
        var selectedEvents = [];
        for (var event in $scope.events) {
          if($scope.events.hasOwnProperty(event)) {
            if ($scope.events[event]) {
              selectedEvents.push(event);
            }
          }
        }
        $scope.errorMessage = "";
        createWebhook($scope.name, $scope.sobject, selectedEvents, $scope.url);
      };
    });

});