(function(window, undefined) {
    var BBBLog = {};

    BBBLog.critical = function() {
      return Function.prototype.bind.call(console.error);
    }();

    BBBLog.error = function() {
      return Function.prototype.bind.call(console.error);
    }();

    BBBLog.warn = function() {
      return Function.prototype.bind.call(console.warn);
    }();

    BBBLog.info = function() {
      return Function.prototype.bind.call(console.info);
    }();

    BBBLog.debug = function() {};

    BBBLog.level = function(level) {
      if (level == "debug") {
        BBBLog.debug = function() {
          return Function.prototype.bind.call(console.info);
        }();
      } else {
        BBBLog.debug = function() {
          return function() {};
        }();
      }
    }

    BBBLog.level("info");
    window.BBBLog = BBBLog;
})(this);
