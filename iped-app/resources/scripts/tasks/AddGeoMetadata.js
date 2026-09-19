/*
 * Javascript processing task to add "common:geo:locations" from other metadata.
 */

function getName() {
    return "AddGeoMetadata";
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
        var geoParsers = {
            "video:com.apple.quicktime.location.ISO6709": parseISO6709,
        };

        for (var metadataName in geoParsers) {
            if (geoParsers.hasOwnProperty(metadataName)) {
                var geodata = metadata.get(metadataName);

                if (geodata) {
                    var geoInfo = geoParsers[metadataName](geodata);

                    if (geoInfo && geoInfo.latitude) {
                        metadata.add(
                            "common:geo:locations",
                            geoInfo.latitude + ";" + geoInfo.longitude
                        );
                        break;
                    }
                }
            }
        }
    }
}
