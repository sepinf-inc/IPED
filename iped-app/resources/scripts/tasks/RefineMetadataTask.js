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

function getRange(datetime, reference) {
  return new Date(datetime).getTime() - new Date(reference).getTime();        
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
 
        var dateTimeContentMetadata = {
            "common:dcterms:created": getRange,
            "image:Exif SubIFD:Date/Time Original": getRange,
            "image:Exif IFD0:Date/Time": getRange,
        };

        for (var metadataName in dateTimeContentMetadata) {
            if (dateTimeContentMetadata.hasOwnProperty(metadataName)) {
                var intDate = metadata.get(metadataName);
                var fsDate = item.getModDate();

                if (intDate && fsDate) {                    
                    var result = new Date(intDate).getTime() - fsDate.getTime();
                    if(result){
                        metadata.add("modToInternalTimeDiff", result);
                    }
                }
            }
        }
    }
}
