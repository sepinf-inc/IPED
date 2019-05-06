package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class Util {

    public static String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static String convertEmojis(String source) {
        StringBuilder str = new StringBuilder();

        char[] chars = source.toCharArray();
        char lastChar = '\0';
        for (int i = 0; i < chars.length; ++i) {
            if (i < chars.length - 1 && Character.isSurrogatePair(chars[i], chars[i + 1])) {
                int codePoint = source.codePointAt(i);
                String key = null;
                if (codePoint >= 0x1f000) {
                    key = String.format("%5x", codePoint); //$NON-NLS-1$
                }
                if (key != null && unicodeFileMap.containsKey(key)) {
                    str.append("<img src=\"" + ReportGenerator.RSRC_PATH + "images/emoji/unicode/") //$NON-NLS-1$ //$NON-NLS-2$
                            .append(unicodeFileMap.get(key))
                            .append(".png\" height=\"20\" width=\"20\"/>"); //$NON-NLS-1$
                } else {
                    str.append(chars[i]).append(chars[i + 1]);
                }
                ++i;
            } else {
                if (Arrays.binarySearch(printable, chars[i]) >= 0) {
                    if (chars[i] == '\n') {
                        str.append("<br/>"); //$NON-NLS-1$
                    } else if (chars[i] == ' ') {
                        if (lastChar == ' ') {
                            str.append("&nbsp;"); //$NON-NLS-1$
                        } else {
                            str.append(chars[i]);
                        }
                    } else {
                        str.append(chars[i]);
                    }
                } else {
                    int codePoint = source.codePointAt(i);
                    String key = null;
                    if (codePoint < 0x2bff) {
                        key = String.format("%04x", codePoint); //$NON-NLS-1$
                    }

                    if (key != null && unicodeFileMap.containsKey(key)) {
                        str.append("<img src=\"" + ReportGenerator.RSRC_PATH + "images/emoji/unicode/") //$NON-NLS-1$ //$NON-NLS-2$
                                .append(unicodeFileMap.get(key))
                                .append(".png\"  height=\"20\" width=\"20\"/>"); //$NON-NLS-1$
                    } else {
                        if (chars[i] == '\n') {
                            str.append("<br/>"); //$NON-NLS-1$
                        } else if (chars[i] == ' ') {
                            str.append("&nbsp;"); //$NON-NLS-1$
                        } else {
                            str.append(chars[i]);
                        }
                    }
                }
            }
            lastChar = chars[i];
        }
        return str.toString();
    }

    public static String getUTF8String(ResultSet rs, String field) throws SQLException {
        String result = null;
        try {
            byte[] bytes = rs.getBytes(field);
            if (bytes != null) {
                result = new String(bytes, "UTF-8"); //$NON-NLS-1$
            }
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
        }

        return result;
    }
    
    public static String getNameFromId(String id) {
    	if (id != null && id.contains("@")) { //$NON-NLS-1$
    		id = id.split("@")[0]; //$NON-NLS-1$
    	}
    	return id;
    }

    private static final char[] printable = ("abcdefghijklmnopqrstuwvxyzABCDEFG" //$NON-NLS-1$
            + "HIJKLMNOPQRSTUVWXYZ ?!@#$%&*0123456789Ã‡Ã§,.;:/\\-_=+\n\r[]{}~|^" //$NON-NLS-1$
            + "'\"<>").toCharArray(); //$NON-NLS-1$

    private final static Map<String, String> unicodeFileMap;

    static {
        Arrays.sort(printable);
        // essa parte do codigo ainda nao esta funcional no IPED
        String unicodeEmojiFiles = "0023-20e3;0030-20e3;0031-20e3;" //$NON-NLS-1$
                + "0032-20e3;0033-20e3;0034-20e3;0035-20e3;0036-20e3;0037-20e3;" //$NON-NLS-1$
                + "0038-20e3;0039-20e3;00a9;00ae;1f004;1f0cf;1f170;1f171;1f17e;" //$NON-NLS-1$
                + "1f17f;1f18e;1f191;1f192;1f193;1f194;1f195;1f196;1f197;1f198;1f199;" //$NON-NLS-1$
                + "1f19a;1f1e8-1f1f3;1f1e9-1f1ea;1f1ea-1f1f8;1f1eb-1f1f7;1f1ec-1f1e7;" //$NON-NLS-1$
                + "1f1ee-1f1f9;1f1ef-1f1f5;1f1f0-1f1f7;1f1f7-1f1fa;1f1fa-1f1f8;1f201;" //$NON-NLS-1$
                + "1f202;1f21a;1f22f;1f232;1f233;1f234;1f235;1f236;1f237;1f238;1f239;" //$NON-NLS-1$
                + "1f23a;1f250;1f251;1f300;1f301;1f302;1f303;1f304;1f305;1f306;1f307;" //$NON-NLS-1$
                + "1f308;1f309;1f30a;1f30b;1f30c;1f30d;1f30e;1f30f;1f310;1f311;1f312;" //$NON-NLS-1$
                + "1f313;1f314;1f315;1f316;1f317;1f318;1f319;1f31a;1f31b;1f31c;1f31d;" //$NON-NLS-1$
                + "1f31e;1f31f;1f320;1f330;1f331;1f332;1f333;1f334;1f335;1f337;1f338;" //$NON-NLS-1$
                + "1f339;1f33a;1f33b;1f33c;1f33d;1f33e;1f33f;1f340;1f341;1f342;1f343;" //$NON-NLS-1$
                + "1f344;1f345;1f346;1f347;1f348;1f349;1f34a;1f34b;1f34c;1f34d;1f34e;" //$NON-NLS-1$
                + "1f34f;1f350;1f351;1f352;1f353;1f354;1f355;1f356;1f357;1f358;1f359;" //$NON-NLS-1$
                + "1f35a;1f35b;1f35c;1f35d;1f35e;1f35f;1f360;1f361;1f362;1f363;1f364;" //$NON-NLS-1$
                + "1f365;1f366;1f367;1f368;1f369;1f36a;1f36b;1f36c;1f36d;1f36e;1f36f;" //$NON-NLS-1$
                + "1f370;1f371;1f372;1f373;1f374;1f375;1f376;1f377;1f378;1f379;1f37a;" //$NON-NLS-1$
                + "1f37b;1f37c;1f380;1f381;1f382;1f383;1f384;1f385;1f386;1f387;1f388;" //$NON-NLS-1$
                + "1f389;1f38a;1f38b;1f38c;1f38d;1f38e;1f38f;1f390;1f391;1f392;1f393;" //$NON-NLS-1$
                + "1f3a0;1f3a1;1f3a2;1f3a3;1f3a4;1f3a5;1f3a6;1f3a7;1f3a8;1f3a9;1f3aa;" //$NON-NLS-1$
                + "1f3ab;1f3ac;1f3ad;1f3ae;1f3af;1f3b0;1f3b1;1f3b2;1f3b3;1f3b4;1f3b5;" //$NON-NLS-1$
                + "1f3b6;1f3b7;1f3b8;1f3b9;1f3ba;1f3bb;1f3bc;1f3bd;1f3be;1f3bf;1f3c0;" //$NON-NLS-1$
                + "1f3c1;1f3c2;1f3c3;1f3c4;1f3c6;1f3c7;1f3c8;1f3c9;1f3ca;1f3e0;1f3e1;" //$NON-NLS-1$
                + "1f3e2;1f3e3;1f3e4;1f3e5;1f3e6;1f3e7;1f3e8;1f3e9;1f3ea;1f3eb;1f3ec;" //$NON-NLS-1$
                + "1f3ed;1f3ee;1f3ef;1f3f0;1f400;1f401;1f402;1f403;1f404;1f405;1f406;" //$NON-NLS-1$
                + "1f407;1f408;1f409;1f40a;1f40b;1f40c;1f40d;1f40e;1f40f;1f410;1f411;" //$NON-NLS-1$
                + "1f412;1f413;1f414;1f415;1f416;1f417;1f418;1f419;1f41a;1f41b;1f41c;" //$NON-NLS-1$
                + "1f41d;1f41e;1f41f;1f420;1f421;1f422;1f423;1f424;1f425;1f426;1f427;" //$NON-NLS-1$
                + "1f428;1f429;1f42a;1f42b;1f42c;1f42d;1f42e;1f42f;1f430;1f431;1f432;" //$NON-NLS-1$
                + "1f433;1f434;1f435;1f436;1f437;1f438;1f439;1f43a;1f43b;1f43c;1f43d;" //$NON-NLS-1$
                + "1f43e;1f440;1f442;1f443;1f444;1f445;1f446;1f447;1f448;1f449;1f44a;" //$NON-NLS-1$
                + "1f44b;1f44c;1f44d;1f44e;1f44f;1f450;1f451;1f452;1f453;1f454;1f455;" //$NON-NLS-1$
                + "1f456;1f457;1f458;1f459;1f45a;1f45b;1f45c;1f45d;1f45e;1f45f;1f460;" //$NON-NLS-1$
                + "1f461;1f462;1f463;1f464;1f465;1f466;1f467;1f468;1f469;1f46a;1f46b;" //$NON-NLS-1$
                + "1f46c;1f46d;1f46e;1f46f;1f470;1f471;1f472;1f473;1f474;1f475;1f476;" //$NON-NLS-1$
                + "1f477;1f478;1f479;1f47a;1f47b;1f47c;1f47d;1f47e;1f47f;1f480;1f481;" //$NON-NLS-1$
                + "1f482;1f483;1f484;1f485;1f486;1f487;1f488;1f489;1f48a;1f48b;1f48c;" //$NON-NLS-1$
                + "1f48d;1f48e;1f48f;1f490;1f491;1f492;1f493;1f494;1f495;1f496;1f497;" //$NON-NLS-1$
                + "1f498;1f499;1f49a;1f49b;1f49c;1f49d;1f49e;1f49f;1f4a0;1f4a1;1f4a2;" //$NON-NLS-1$
                + "1f4a3;1f4a4;1f4a5;1f4a6;1f4a7;1f4a8;1f4a9;1f4aa;1f4ab;1f4ac;1f4ad;" //$NON-NLS-1$
                + "1f4ae;1f4af;1f4b0;1f4b1;1f4b2;1f4b3;1f4b4;1f4b5;1f4b6;1f4b7;1f4b8;" //$NON-NLS-1$
                + "1f4b9;1f4ba;1f4bb;1f4bc;1f4bd;1f4be;1f4bf;1f4c0;1f4c1;1f4c2;1f4c3;" //$NON-NLS-1$
                + "1f4c4;1f4c5;1f4c6;1f4c7;1f4c8;1f4c9;1f4ca;1f4cb;1f4cc;1f4cd;1f4ce;" //$NON-NLS-1$
                + "1f4cf;1f4d0;1f4d1;1f4d2;1f4d3;1f4d4;1f4d5;1f4d6;1f4d7;1f4d8;1f4d9;" //$NON-NLS-1$
                + "1f4da;1f4db;1f4dc;1f4dd;1f4de;1f4df;1f4e0;1f4e1;1f4e2;1f4e3;1f4e4;" //$NON-NLS-1$
                + "1f4e5;1f4e6;1f4e7;1f4e8;1f4e9;1f4ea;1f4eb;1f4ec;1f4ed;1f4ee;1f4ef;" //$NON-NLS-1$
                + "1f4f0;1f4f1;1f4f2;1f4f3;1f4f4;1f4f5;1f4f6;1f4f7;1f4f9;1f4fa;1f4fb;" //$NON-NLS-1$
                + "1f4fc;1f500;1f501;1f502;1f503;1f504;1f505;1f506;1f507;1f508;1f509;" //$NON-NLS-1$
                + "1f50a;1f50b;1f50c;1f50d;1f50e;1f50f;1f510;1f511;1f512;1f513;1f514;" //$NON-NLS-1$
                + "1f515;1f516;1f517;1f518;1f519;1f51a;1f51b;1f51c;1f51d;1f51e;1f51f;" //$NON-NLS-1$
                + "1f520;1f521;1f522;1f523;1f524;1f525;1f526;1f527;1f528;1f529;1f52a;" //$NON-NLS-1$
                + "1f52b;1f52c;1f52d;1f52e;1f52f;1f530;1f531;1f532;1f533;1f534;1f535;" //$NON-NLS-1$
                + "1f536;1f537;1f538;1f539;1f53a;1f53b;1f53c;1f53d;1f550;1f551;1f552;" //$NON-NLS-1$
                + "1f553;1f554;1f555;1f556;1f557;1f558;1f559;1f55a;1f55b;1f55c;1f55d;" //$NON-NLS-1$
                + "1f55e;1f55f;1f560;1f561;1f562;1f563;1f564;1f565;1f566;1f567;1f5fb;" //$NON-NLS-1$
                + "1f5fc;1f5fd;1f5fe;1f5ff;1f600;1f601;1f602;1f603;1f604;1f605;1f606;" //$NON-NLS-1$
                + "1f607;1f608;1f609;1f60a;1f60b;1f60c;1f60d;1f60e;1f60f;1f610;1f611;" //$NON-NLS-1$
                + "1f612;1f613;1f614;1f615;1f616;1f617;1f618;1f619;1f61a;1f61b;1f61c;" //$NON-NLS-1$
                + "1f61d;1f61e;1f61f;1f620;1f621;1f622;1f623;1f624;1f625;1f626;1f627;" //$NON-NLS-1$
                + "1f628;1f629;1f62a;1f62b;1f62c;1f62d;1f62e;1f62f;1f630;1f631;1f632;" //$NON-NLS-1$
                + "1f633;1f634;1f635;1f636;1f637;1f638;1f639;1f63a;1f63b;1f63c;1f63d;" //$NON-NLS-1$
                + "1f63e;1f63f;1f640;1f645;1f646;1f647;1f648;1f649;1f64a;1f64b;1f64c;" //$NON-NLS-1$
                + "1f64d;1f64e;1f64f;1f680;1f681;1f682;1f683;1f684;1f685;1f686;1f687;" //$NON-NLS-1$
                + "1f688;1f689;1f68a;1f68b;1f68c;1f68d;1f68e;1f68f;1f690;1f691;1f692;" //$NON-NLS-1$
                + "1f693;1f694;1f695;1f696;1f697;1f698;1f699;1f69a;1f69b;1f69c;1f69d;" //$NON-NLS-1$
                + "1f69e;1f69f;1f6a0;1f6a1;1f6a2;1f6a3;1f6a4;1f6a5;1f6a6;1f6a7;1f6a8;" //$NON-NLS-1$
                + "1f6a9;1f6aa;1f6ab;1f6ac;1f6ad;1f6ae;1f6af;1f6b0;1f6b1;1f6b2;1f6b3;" //$NON-NLS-1$
                + "1f6b4;1f6b5;1f6b6;1f6b7;1f6b8;1f6b9;1f6ba;1f6bb;1f6bc;1f6bd;1f6be;" //$NON-NLS-1$
                + "1f6bf;1f6c0;1f6c1;1f6c2;1f6c3;1f6c4;1f6c5;203c;2049;2122;2139;2194;" //$NON-NLS-1$
                + "2195;2196;2197;2198;2199;21a9;21aa;231a;231b;23e9;23ea;23eb;23ec;" //$NON-NLS-1$
                + "23f0;23f3;24c2;25aa;25ab;25b6;25c0;25fb;25fc;25fd;25fe;2600;2601;" //$NON-NLS-1$
                + "260e;2611;2614;2615;261d;263a;2648;2649;264a;264b;264c;264d;264e;" //$NON-NLS-1$
                + "264f;2650;2651;2652;2653;2660;2663;2665;2666;2668;267b;267f;2693;" //$NON-NLS-1$
                + "26a0;26a1;26aa;26ab;26bd;26be;26c4;26c5;26ce;26d4;26ea;26f2;26f3;" //$NON-NLS-1$
                + "26f5;26fa;26fd;2702;2705;2708;2709;270a;270b;270c;270f;2712;2714;" //$NON-NLS-1$
                + "2716;2728;2733;2734;2744;2747;274c;274e;2753;2754;2755;2757;2764;" //$NON-NLS-1$
                + "2795;2796;2797;27a1;27b0;27bf;2934;2935;2b05;2b06;2b07;2b1b;2b1c;" //$NON-NLS-1$
                + "2b50;2b55;3030;303d;3297;3299"; //$NON-NLS-1$
        unicodeFileMap = new HashMap<>();
        String[] itens = unicodeEmojiFiles.split(";"); //$NON-NLS-1$
        for (String item : itens) {
            if (item.contains("-")) { //$NON-NLS-1$
                unicodeFileMap.put(item.split("-")[0], item); //$NON-NLS-1$
                unicodeFileMap.put(item.split("-")[1], item); //$NON-NLS-1$
            } else {
                unicodeFileMap.put(item, item);
            }
        }
    }
}
