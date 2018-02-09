(function(window, undefined) {
    var BBBLogger = {};

    BBBLogger.error = function() {
      return Function.prototype.bind.call(console.error);
    }();

    BBBLogger.warn = function() {
      return Function.prototype.bind.call(console.warn);
    }();

    BBBLogger.info = function() {
      return Function.prototype.bind.call(console.info);
    }();

    BBBLogger.debug = function() {};

    BBBLogger.level = function(level) {
      if (level == "debug") {
        BBBLogger.debug = function() {
          return Function.prototype.bind.call(console.debug);
        }();
      } else {
        BBBLogger.debug = function() {
          return function() {};
        }();
      }
    }

    BBBLogger.level("info");
    window.Logger = BBBLogger;
})(this);
