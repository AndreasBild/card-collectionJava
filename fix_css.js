
const fs = require("fs");
const stylelint = require("stylelint");

stylelint.lint({
  files: "src/main/resources/css/**/*.css",
  config: {
    extends: "stylelint-config-standard",
    rules: {
      "no-descending-specificity": null,
      "declaration-block-single-line-max-declarations": null,
      "keyframes-name-pattern": null
    }
  },
  fix: true
}).then(function(resultObject) {
  if (resultObject.errored) {
    console.log(resultObject.output);
  } else {
    console.log("No stylelint errors found after applying fixes!");
  }
}).catch(function(err) {
  console.error(err.stack);
});
