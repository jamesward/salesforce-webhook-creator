require(["angular"], function(angular) {

  angular.module('myApp', []).
    controller('WebhooksController', function($scope, $http) {

      var fetchWebhooks = function() {
        $http.get('/webhooks').
          success(function(data) {
            $scope.webhooks = data;
          }).
          error(function(data) {
            $scope.errorMessage = data.error.message;
          });
      };

      var createWebhook = function(name, sobject, events, url, csrfToken) {
        var data = {
          name: name,
          sobject: sobject,
          events: events,
          url: url,
          csrfToken: csrfToken
        };

        $http.post('/webhooks?csrfToken=' + csrfToken, data).
          success(function() {
            initForm();
            fetchWebhooks();
          }).
          error(function(data) {
            $scope.working = false;
            $scope.errorMessage = data.error.message;
          });
      };

      var fetchSobjects = function() {
        $http.get('/sobjects').
          success(function(data) {
            $scope.sobjects = data;
          }).
          error(function(data) {
            $scope.errorMessage = data.error.message;
          });
      };

      var initForm = function() {
        if ($scope.errorMessage === undefined) {
          $scope.errorMessage = "";
        }

        $scope.working = false;
        $scope.name = "";
        $scope.sobject = null;
        $scope.events = {};
        $scope.url = "";
      };

      var selectedEvents = function() {
        var selectedEvents = [];
        for (var event in $scope.events) {
          if($scope.events.hasOwnProperty(event)) {
            if ($scope.events[event]) {
              selectedEvents.push(event);
            }
          }
        }
        return selectedEvents;
      };

      fetchWebhooks();
      fetchSobjects();
      initForm();

      $scope.createWebhook = function() {
        if ($scope.webhookForm.name.$valid && $scope.webhookForm.sobject.$valid && selectedEvents().length > 0 && $scope.webhookForm.url.$valid) {
          $scope.errorMessage = "";
          $scope.working = true;
          createWebhook($scope.name, $scope.sobject, selectedEvents(), $scope.url, $scope.csrfToken);
        }
        else {
          var error = "";
          if ($scope.webhookForm.name.$invalid) {
            error += "Name must contain only letters.  ";
          }
          if ($scope.webhookForm.sobject.$invalid) {
            error += "An SObject must be selected.  ";
          }
          if (selectedEvents().length < 1) {
            error += "At least one event must be selected.  ";
          }
          if ($scope.webhookForm.url.$invalid) {
            error += "A URL must be specified.  ";
          }
          $scope.errorMessage = error;
        }
      };
    });

});
