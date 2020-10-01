
var fs = require('fs');
var path = require('path');


function addOnNewIntent(match, p1,p2, p3, p4, offset, string){
    if(!p3.includes("SharedPreferencesHelper")){
        var addingimport ="import com.unarin.cordova.beacon.SharedPreferencesHelper;";
        var addingcode = "SharedPreferencesHelper sharedPrefHelper;if (extras != null) {if (extras.getBoolean(\"cdvStartInBackground\", false)) {moveTaskToBack(true);}if (extras.containsKey(\"DeepLinkID\")) {String deepLinkID = extras.getString(\"DeepLinkID\");sharedPrefHelper = new SharedPreferencesHelper(\"NotificationsBD\", getApplicationContext());sharedPrefHelper.setDeepLink(deepLinkID);}}"
        return [p1,addingimport,p2,addingcode,p4].join("");
    }else{
        return [p1,p2,p3,p4].join("");
    }
}
module.exports = function (context) {
    
    console.log("Start changing MainActivity!");
    var Q = context.requireCordovaModule("q");
    var deferral = new Q.defer();


    var rawConfig = fs.readFileSync("config.xml", 'ascii');
    var match = /^<widget.+ id="([\S]+)".+?>$/gm.exec(rawConfig);
    if(!match || match.length != 2){
        throw new Error("id parse failed");
    }

    var id = match[1];
    var regexId = new RegExp("\\.","g");
    id = id.replace(regexId,"/")
    

    var projectRoot = context.opts.cordova.project ? context.opts.cordova.project.root : context.opts.projectRoot;
    var activityPath = path.join(projectRoot,"platforms","android","app","src","main","java",id,"MainActivity.java");
    var activity = fs.readFileSync(activityPath, "utf8");


    var regex = /(package[\s|\S]*)(public class[\s|\S]*getExtras\(\);)([\s|\S]*)(loadUrl[\s|\S|}]*)/gm;
    activity = activity.replace(regex,addOnNewIntent);

    
    fs.writeFileSync(activityPath, activity);
    console.log("Finished changing MainActivity!");
    deferral.resolve();

    return deferral.promise;
}