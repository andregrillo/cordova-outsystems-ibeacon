
var fs = require('fs');
var path = require('path');
var xml2js = require('xml2js');


function addOnNewIntent(match, p1, p2, offset, string){
    if(!p2.includes("private SharedPreferencesHelper sharedPrefHelper;")){
        var adding = "private SharedPreferencesHelper sharedPrefHelper;\n@Override\nprotected void onNewIntent(Intent intent) {super.onNewIntent(intent);if (intent != null){Bundle extras = intent.getExtras();if (extras != null){if(extras.containsKey(\"DeepLinkID\")){String deepLinkID = extras.getString(\"DeepLinkID\");if(sharedPrefHelper == null){sharedPrefHelper = new SharedPreferencesHelper(\"NotificationsBD\",getApplicationContext());}sharedPrefHelper.setDeepLink(deepLinkID);}}}}"
        return [p1,adding,p2].join("");
    }else{
        return [p1,p2].join("");
    }
}
function loadConfigXMLDoc(filePath) {
    var json = "";
    try {
        var fileData = fs.readFileSync(filePath, 'ascii');
        var parser = new xml2js.Parser();
        parser.parseString(fileData.substring(0, fileData.length), function (err, result) {
            //console.log("config.xml as JSON", JSON.stringify(result, null, 2));
            json = result;
        });
        console.log("File '" + filePath + "' was successfully read.");
        return json;
    } catch (ex) {
        console.log(ex)
    }
}
module.exports = function (context) {

    console.log("Start changing MainActivity!");
    var Q = context.requireCordovaModule("q");
    var deferral = new Q.defer();

    var projectRoot = context.opts.cordova.project ? context.opts.cordova.project.root : context.opts.projectRoot;
    var configXML = loadConfigXMLDoc("config.xml");
    var appPath = configXML.widget.$.id;
    appPath = appPath.replace(".","/");
    var activityPath = path.join(projectRoot,"platforms","android","app","src","main","java",appPath,"MainActivity.java");
    console.log("teste:"+activityPath);
    var activity = fs.readFileSync(activityPath, "utf8");

    var regex = /([\s|\S]*loadUrl\(launchUrl\);\n\s+})([\s|\S]*})/gm;
    activity = activity.replace(regex,addOnNewIntent);

    
    fs.writeFileSync(activityPath, activity);
    console.log("Finished changing MainActivity!");
    deferral.resolve();

    return deferral.promise;
}