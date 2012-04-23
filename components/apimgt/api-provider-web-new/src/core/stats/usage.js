var apiProvider = require("/core/greg/greg.js").getAPIProviderObj();

var getAPIUsageHostTest = function(apiName, server) {
    var log = new Log();
    var usage = [];
    var usageout = [];
    try {
        usage = apiProvider.getAPIUsageTest(apiName, server);
        if (log.isDebugEnabled()) {
            log.debug("getAPIUsageHostTest for : " + server);
        }
        for (var k = 0; k < usage.length; k++) {
            var elem = {
                version:usage[k].version,
                count:usage[k].count
            };
            usageout.push(elem);


        }
        return {
            error:false,
            usage:usageout
        };
    } catch (e) {
        log.error(e.message);
        return {
            error:e,
            usage:null

        };
    }
};


var getAllAPIUsage = function(server) {
    var usage = [];
    var usageout = [];
    try {
        usage = apiProvider.getAllAPIUsage(server);
        if (log.isDebugEnabled()) {
            log.debug("getAllAPIUsage for : " + server);
        }
        if (usage == null) {
            return {
                error:true,
                usage:null
            };

        } else {
            for (var k = 0; k < usage.length; k++) {
                var elem = {
                    apiName:usage[k].apiName,
                    count:usage[k].count
                };
                usageout.push(elem);


            }
            return {
                error:false,
                usage:usageout
            };
        }
    } catch (e) {
        log.error(e.message);
        return {
            error:e,
            usage:null

        };
    }
};
