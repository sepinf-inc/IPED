/*
 * Javascript processing task to derive some new metadata info from other metadata or item content. 
 */

function getName() {
    return "RefineMetadataTask";
}

function getConfigurables() {
    return null;
}

function init(configuration) {
}

function finish() {
}

function parseISO6709(location) {
    var match = location.match(
        /^([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)(?:([+-]\d+(?:\.\d+)?))?\/?$/
    );

    if (!match) {
        return null;
    }

    return {
        latitude: parseFloat(match[1]),
        longitude: parseFloat(match[2]),
        altitude: match[3] !== undefined ? parseFloat(match[3]) : null
    };
}

function process(item) {
    var metadata = item.getMetadata();

    if (metadata) {

        // GEO LOCATIONS (#2983)
        // Set "common:geo:locations" when other geo location metadata is present.

        var geoParsers = {
            "video:com.apple.quicktime.location.ISO6709": parseISO6709,
        };

        for (var metadataName in geoParsers) {
            if (geoParsers.hasOwnProperty(metadataName)) {
                var geodata = metadata.get(metadataName);

                if (geodata) {
                    var geoInfo = geoParsers[metadataName](geodata);

                    if (geoInfo && geoInfo.latitude) {
                        metadata.add("common:geo:locations", geoInfo.latitude + ";" + geoInfo.longitude);
                        break;
                    }
                }
            }
        }

        // POSSIBLY PRODUCED (#2934)
        // Set "modToInternalTimeDiff" with the difference between file modified date
        // and "internal" (EXIF, PDF) dates.

        var dateTimeContentMetadata = [
            "common:dcterms:created",
            "image:Exif SubIFD:Date/Time Original",
            "image:Exif IFD0:Date/Time"
        ];

        var fsDate = item.getModDate();

        if (fsDate) {
            var maxDiff = 365 * 86400 * 20; // ~20 years (in seconds)
            var minDiff = maxDiff;

            var DateUtil = Java.type('iped.utils.DateUtil');

            for (var i = 0; i < dateTimeContentMetadata.length; i++) {
                var metadataName = dateTimeContentMetadata[i];            
                var intDate = metadata.get(metadataName);

                if (intDate) {
                    var val = DateUtil.tryToParseDate(intDate);
                    if (val != null) { 
                        var diff = val.getTime() - fsDate.getTime();
                        diff = Math.round(diff / 1000);
                        if (Math.abs(diff) < Math.abs(minDiff)) {
                            minDiff = diff;
                        }
                    }
                }
            }

            if (minDiff < maxDiff) {
                metadata.set("modToInternalTimeDiff", minDiff);
            }
        }
    }
}
