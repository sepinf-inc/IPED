
package iped.engine.task.die;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class Die {
    private static final float INF = -1e10f;
    private static final int numStripes1 = 3, numStripes2 = 5, grid1 = 6, grid2 = 11, grid3 = 3, grid4 = 5, grid5 = 7;
    private static final int size = 256;
    private static final int binSize = 25;
    private static final short[] sqrt = new short[1 << 20];
    private static final short[] atan = new short[1 << 22];
    static {
        for (int i = 0; i < 1000; i++) {
            int i10 = i << 10;
            for (int j = 0; j < 1000; j++) {
                sqrt[i10 | j] = (short) ((int) Math.sqrt((i * i + j * j) / 2.0));
            }
        }
        for (int i = -1024; i < 1024; i++) {
            int i11 = (i + 1024) << 11;
            for (int j = -1024; j < 1024; j++) {
                atan[i11 | (j + 1024)] = (short) ((int) ((Math.atan2(i, j) + Math.PI) * 999 / (Math.PI * 2)));
            }
        }
    }
    private static final int[] bestFeatures = new int[] { 12082, 1107, 12083, 11651, 4431, 13784, 12056, 12812, 13783,
            11641, 11191, 12046, 4461, 11650, 11299, 11163, 11209, 11623, 11344, 13757, 11487, 12811, 12001, 12055,
            11317, 11380, 1106, 11649, 4401, 12783, 11217, 12432, 11155, 4460, 12802, 13756, 12784, 11479, 11489, 12081,
            4430, 11182, 3343, 11631, 11627, 12434, 5102, 839, 3583, 12019, 12054, 13702, 3581, 11290, 12073, 13972, 37,
            36, 3823, 11465, 12792, 3821, 13594, 11352, 1137, 3103, 11464, 4400, 13701, 12110, 11677, 837, 11272, 13747,
            13973, 11168, 11244, 12036, 1136, 12893, 11371, 699, 12109, 11488, 11276, 1077, 14406, 5088, 13811, 11678,
            1108, 3341, 11626, 13737, 11194, 12788, 12433, 3352, 848, 3592, 1370, 12810, 13595, 12424, 1269, 1273,
            12839, 11622, 1076, 11192, 5008, 11625, 1447, 13782, 11218, 12005, 12409, 3578, 12731, 2863, 13810, 11462,
            2761, 38, 11165, 1272, 13688, 13753, 13946, 3831, 11381, 11300, 11600, 12808, 5252, 11140, 11516, 12031,
            12786, 126, 11974, 5226, 11379, 12032, 11704, 4432, 5254, 161, 11614, 65, 2763, 13720, 3824, 12028, 1109,
            5224, 11407, 13732, 3112, 12515, 39, 1403, 11978, 2771, 11236, 11469, 12838, 12380, 11353, 3832, 5134,
            11245, 12100, 12410, 12865, 2783, 12029, 5248, 0, 13755, 11325, 5258, 11190, 1358, 13690, 1309, 11542,
            13774, 11676, 11354, 2741, 3787, 12703, 12030, 845, 834, 2764, 11597, 12353, 3338, 2757, 11219, 11992,
            13460, 3349, 11398, 11948, 13780, 11303, 3589, 3825, 11141, 12002, 12326, 13731, 12787, 11337, 3818, 5077,
            13568, 13703, 1445, 5256, 3591, 11154, 3817, 3577, 147, 2743, 5065, 2791, 4967, 1388, 3830, 11647, 12414,
            12785, 3350, 12000, 11435, 5130, 11246, 13729, 11327, 18, 5250, 11202, 2768, 4462, 12190, 2623, 4928, 5120,
            12622, 11668, 14216, 2755, 559, 846, 5162, 3584, 11408, 4971, 13764, 3590, 1356, 4953, 114, 4881, 12027,
            841, 3109, 2751, 4581, 11812, 12790, 3110, 3585, 1405, 3829, 11298, 12059, 11215, 1283, 13730, 4883, 12419,
            13758, 1431, 11406, 4886, 4909, 4965, 5222, 11474, 12488, 13675, 1363, 5071, 12730, 1349, 2803, 5128, 1867,
            3588, 4872, 5101, 706, 11195, 11208, 11705, 4949, 11543, 12052, 12677, 105, 11329, 11654, 2693, 2723, 708,
            2760, 2781, 2740, 13726, 68, 2869, 11326, 11147, 11164, 14409, 4281, 4943, 13891, 4942, 11624, 5228, 12569,
            1348, 12044, 40, 159, 4939, 11310, 14407, 2744, 158, 507, 4923, 5116, 4932, 5125, 12039, 1282, 1442, 2686,
            2784, 12623, 13082, 13689, 5131, 12461, 13682, 844, 2673, 4867, 104, 11330, 1446, 3828, 11759, 847, 2143,
            3351, 4937, 11433, 11634, 13733, 3101, 5114, 13971, 2690, 5160, 5244, 13513, 705, 3098, 12066, 12772, 3345,
            11201, 13760, 5043, 5106, 11224, 12108, 12837, 15576, 4895, 4941, 4869, 4890, 1372, 2383, 4893, 5111, 12946,
            34, 3815, 5109, 11460, 833, 2721, 11629, 111, 1110, 1391, 4490, 11364, 12892, 13801, 112, 2788, 11636,
            13055, 14408, 1078, 4951, 5113, 15738, 13706, 13945, 2748, 4929, 5098, 4914, 3348, 144, 5085, 5091, 78,
            1355, 2630, 11643, 11732, 2870, 11336, 1268, 119, 1902, 13735, 13787, 419, 3834, 4925, 11478, 12829, 694,
            5158, 12086, 565, 11384, 12079, 12757, 4977, 12063, 12218, 11, 647, 4946, 12596, 13567, 121, 5097, 11663,
            697, 2629, 4968, 5078, 5083, 1138, 3105, 4402, 5242, 12012, 30, 4935, 659, 2872, 4954, 11161, 15, 11413,
            2823, 4963, 12041, 12407, 145, 838, 2389, 2703, 5127, 12034, 5099, 13001, 4876, 4947, 5186, 5246, 3820,
            5129, 11263, 11316, 11599, 11921, 4433, 4911, 5092, 11485, 13324, 107, 639, 2728, 2772, 4945, 11463, 11989,
            12048, 12761, 15043, 2724, 2683, 2756, 5115, 5196, 11391, 11472, 11639, 13684, 4905, 11570, 12804, 8, 16,
            79, 5095, 12815, 13807, 13944, 4849, 4940, 1443, 3337, 12824, 12883, 12919, 14988, 12451, 1428, 5230, 3575,
            4611, 5105, 701, 5188, 13674, 2674, 4969, 11283, 11363, 12068, 12760, 12835, 4957, 4959, 5087, 5212, 12770,
            13696, 15746, 637, 5182, 12795, 12806, 13621, 1420, 2774, 4973, 5180, 5198, 11343, 14000, 1454, 2862, 4841,
            4899, 11302, 13778, 14990, 1260, 2858, 11350, 2685, 2754, 12058, 13759, 1294, 2142, 5119, 4960, 13699, 128,
            519, 1018, 2708, 3023, 5100, 5200, 11228, 2735, 2759, 4927, 5164, 5234, 13749, 64, 421, 568, 698, 1323,
            1344, 2765, 4491, 12974, 1351, 2865, 4827, 4879, 5210, 11840, 12025, 12891, 2777, 4907, 4915, 11645, 12299,
            14481, 2390, 4955, 11377, 1389, 1402, 2622, 5047, 5206, 12093, 13679, 13809, 4889, 4970, 11357, 4926, 5190,
            12050, 12947, 13593, 13740, 1433, 2670, 3102, 5194, 5238, 11356, 12191, 12775, 15585, 566, 1104, 2801, 5184,
            5218, 11386, 11813, 13728, 13751, 558, 1905, 2632, 2730, 13685, 13767, 14989, 1866, 3111, 4311, 11222,
            11985, 12423, 14991, 499, 840, 1322, 1401, 5133, 5137, 5192, 13769, 13909, 35, 418, 2722, 4861, 5123, 5236,
            11242, 12042, 14080, 1903, 2739, 3342, 3582, 4829, 5093, 1365, 5208, 11359, 2150, 2661, 2688, 2790, 3344,
            2805, 4917, 11514, 11674, 13433, 13686, 29, 102, 11951, 12397, 1285, 1910, 2694, 2747, 4839, 5170, 13745,
            69, 504, 4837, 5132, 11273, 11309, 11404, 12106, 13999, 2543, 2727, 5066, 5220, 5240, 497, 1357, 2625, 2738,
            1429, 11148, 12378, 13762, 33, 1909, 5048, 3011, 11661, 12057, 1346, 1378, 2861, 3822, 3827, 4807, 4898,
            11598, 12061, 13776, 1079, 2149, 2710, 2731, 2750, 3340, 5202, 11229, 12822, 4919, 4956, 5020, 2701, 2758,
            3594, 4931, 4974, 11984, 12430, 14135, 2720, 2769, 2778, 11422, 15042, 9, 425, 1105, 2503, 2780, 4814, 4875,
            11275, 11785, 2145, 2523, 2770, 4900, 11506, 12436, 12771, 12800, 15586, 2792, 11340, 2804, 4933, 11483,
            12163, 12764, 15747, 125, 1139, 5067, 5082, 11420, 12798, 13678, 13693, 13823, 15584, 644, 1308, 5168, 5232,
            428, 1912, 2983, 4310, 4871, 11342, 11973, 1369, 1390, 2762, 5005, 5214, 11175, 11434, 12245, 12459, 13770,
            13794, 1362, 2734, 2752, 2825, 3108, 5086, 11167, 12721, 13352, 13406, 4885, 13743, 2382, 3819, 4280, 4825,
            4877, 11607, 12004, 12038, 12797, 13713, 707, 2563, 4403, 11334, 11965, 12758, 15748, 2821, 4897, 5084,
            5140, 12814, 15044, 836, 2666, 12075, 12408, 2531, 4809, 13325, 619, 2736, 5166, 5216, 15745, 11620, 11640,
            12650, 163, 648, 2789, 4463, 11231, 4912, 11656, 12009, 12065, 12095, 12486, 13405, 1198, 2811, 5006, 5033,
            12768, 2303, 2718, 5059, 5107, 12920, 15046, 426, 561, 2749, 2775, 13566, 25, 1361, 4816, 11235, 11249,
            11673, 1368, 2767, 2794, 4891, 14324, 113, 2687, 2704, 4921, 5144, 5176, 11188, 11370, 11452, 1286, 1312,
            2737, 4580, 5034, 11414, 11606, 12417, 12781, 13379, 14189, 94, 157, 2707, 2719, 3263, 3576, 4904, 5081,
            5117, 11199, 11653, 11894, 15045, 5019, 11205, 11282, 12122, 12767, 508, 2392, 3003, 3031, 4873, 4901,
            11271, 12077, 12272, 1338, 2776, 3503, 4429, 5172, 11296, 13704, 1256, 1343, 2385, 3580, 4582, 4918, 11983,
            13622, 13705, 13969, 2824, 3043, 4884, 11977, 15036, 2725, 11982, 12003, 12766, 14161, 2669, 2680, 2706,
            3816, 4870, 1359, 2541, 3833, 11274, 12037, 12729, 2785, 2827, 3587, 4903, 11893, 13531, 100, 466, 2681,
            2726, 2808, 4913, 5121, 5152, 5156, 12382, 14992, 831, 1135, 1354, 2820, 3579, 4371, 5142, 15009, 2662,
            2682, 2732, 5204, 13786, 479, 2152, 5154, 11241, 11613, 11637, 835, 2665, 4961, 5035, 13746, 1350, 2779,
            2800, 5079, 5118, 12437, 961, 1418, 1713, 2711, 2729, 2812, 4610, 14162, 14766, 45, 134, 799, 2283, 2787,
            11427, 11988, 12821, 13190, 13942, 1196, 1281, 2663, 11415, 12675, 12817, 13765, 14955, 15583, 4887, 11757,
            12137, 15744, 517, 4282, 4865, 5148, 11998, 12069, 12072, 13163, 13710, 13736, 13936, 14604, 15041, 640,
            2715, 11174, 11595, 13695, 14427, 2871, 3008, 4978, 12778, 12828, 41, 2746, 11256, 13773, 13796, 1432, 2689,
            3243, 11471, 12801, 13739, 31, 832, 11207, 11315, 11390, 11467, 14297, 281, 657, 704, 1416, 2713, 5135,
            12542, 12834, 13683, 696, 1267, 1873, 2742, 11289, 12007, 12383, 13712, 13919, 124, 1300, 1706, 2709, 2981,
            11339, 11512, 11660, 11667, 11867, 14562, 2773, 12043, 12416, 14108, 1427, 2063, 2705, 2810, 4459, 5012,
            5070, 5174, 11187, 11323, 12460, 13793, 1311, 2528, 4845, 4853, 13963, 15611, 15774, 44, 2583, 2618, 11160,
            11981, 12045, 12064, 13681, 15562, 28, 641, 850, 1441, 2766, 4399, 11457, 12092, 12405, 12478, 13715, 14081,
            14378, 14724, 15711, 26, 32, 2745, 2782, 3024, 5146, 11301, 11397, 11425, 11611, 11987, 12271, 12370, 12406,
            13785, 2621, 2828, 4370, 4855, 4859, 5075, 11976, 12794, 13864, 81, 1404, 2043, 2483, 3028, 3463, 12831,
            606, 988, 1048, 2822, 5061, 13772, 1017, 2378, 11670, 13805, 2453, 3097, 3223, 4975, 11221, 11758, 12016,
            118, 282, 554, 1134, 1444, 2501, 4372, 4833, 4981, 11633, 12676, 12763, 13486, 13738, 242, 1075, 1983, 2692,
            2802, 2860, 3794, 4831, 5089, 5104, 12780, 12873, 13742, 15775, 146, 2023, 2826, 5178, 11328, 11366, 11730,
            13558, 13744, 14535, 15772, 501, 609, 849, 2323, 2716, 4994, 11383, 11609, 11632, 12706, 15021, 106, 469,
            2831, 3100, 5031, 5150, 11411, 11596, 11603, 12085, 12428, 12429, 13763, 13766, 15582, 42, 215, 359, 1255,
            3021, 3483, 4434, 4612, 4821, 4838, 5073, 13244, 1168, 2691, 11575, 12008, 12018, 12412, 12759, 13652,
            13771, 13789, 27, 2521, 5103, 11995, 12014, 12071, 12868, 14982, 4, 664, 1373, 2753, 2798, 3283, 5069,
            11204, 13028, 2698, 2714, 11181, 12102, 12765, 599, 2806, 5126, 11619, 12127, 12420, 12702, 13564, 13838,
            14270, 2343, 3303, 3767, 11313, 12621, 12819, 13591, 13717, 14987, 15773, 500, 1103, 1325, 2263, 2659, 4492,
            11592, 12105, 12820, 12864, 12889, 13709, 13718, 14351, 222, 1243, 1293, 1452, 2696, 11349, 11754, 12088,
            13000, 13708, 13725, 13800, 14967, 620, 963, 3004, 11287, 12244, 14447, 14590, 14752, 2548, 2793, 5015,
            11393, 11418, 11602, 12135, 12727, 12748, 12866, 13921, 1197, 1298, 2138, 2660, 12694, 13297, 13692, 67,
            117, 521, 1340, 2561, 2679, 2830, 11367, 11646, 11808, 12011, 12756, 14442, 528, 660, 779, 941, 1258, 1371,
            2243, 4312, 4812, 4835, 4850, 11361, 11991, 12113, 12704, 13060, 13585, 15724, 505, 1440, 2508, 2702, 4993,
            11604, 11958, 12235, 13719, 56, 132, 1284, 1430, 2678, 4851, 4862, 11170, 11322, 12793, 13716, 13722, 13792,
            85, 136, 1843, 2544, 2551, 2675, 2677, 4252, 5112, 11166, 11538, 12021, 12136, 12774, 12856, 13378, 13612,
            13996, 14697, 15047, 1898, 2676, 2712, 2796, 2799, 3523, 11335, 11980, 12099, 12351, 13666, 13797, 160, 506,
            524, 1016, 2524, 2671, 2786, 4552, 11533, 11703, 11920, 12779, 12964, 13217, 379, 486, 1047, 2631, 2658,
            2733, 2818, 2913, 3783, 3807, 12023, 12035, 12457, 13698, 13836, 14928, 487, 493, 617, 1271, 2700, 3063,
            3593, 4920, 5076, 11565, 12791, 12945, 13135, 115 };
    public static final int numFeatures = bestFeatures.length;
    private static final int numRawFeatures = 16138;

    public static int getExpectedImageSize() {
        return size;
    }

    public static List<Float> extractFeatures(BufferedImage img) {
        if (img == null)
            return null;
        List<Float> features = null;
        try {
            short[] ca = new short[250];
            int[] maxDist = new int[1000];
            short[] bc = new short[1000 / binSize];
            features = new ArrayList<Float>(numRawFeatures);
            SortedMap<Integer, List<Float>> idxFeatures = new TreeMap<Integer, List<Float>>();
            int width = img.getWidth();
            int height = img.getHeight();
            features.add((float) (width == 0 || height == 0 ? 0
                    : width >= height ? 4 * width / height : -4 * height / width));
            img = resizeImage(img);
            if (img == null)
                return null;

            int[][] channels = getChannels(img);
            int[] hue = channels[0];
            int[] saturation = channels[1];
            int[] lightness = channels[2];
            int[] gray = channels[3];
            int[] red = channels[4];
            int[] green = channels[5];
            int[] blue = channels[6];
            boolean grayscale = channels[7] != null;

            addAll(features, null, -1, rectStatFeaturesAng(bc, ca, maxDist, grayscale, hue, 0, 0, size, size, size));
            addAll(features, null, -1, rectStatFeatures(bc, grayscale, saturation, 0, 0, size, size, size));
            addAll(features, null, -1, rectStatFeatures(bc, false, lightness, 0, 0, size, size, size));
            features.addAll(rectBinCount(grayscale, hue, 0, 0, size, size, size));
            for (int step = 1; step <= 2; step++) {
                int numStripes = step == 1 ? numStripes1 : numStripes2;
                for (int i = 0; i < numStripes; i++) {
                    addAll(features, idxFeatures, step + 0, rectStatFeaturesAng(bc, ca, maxDist, grayscale, hue, 0,
                            size * i / numStripes, size, size / numStripes, size));
                    addAll(features, idxFeatures, step + 2, rectStatFeaturesAng(bc, ca, maxDist, grayscale, hue,
                            size * i / numStripes, 0, size / numStripes, size, size));
                    addAll(features, idxFeatures, step + 4, rectStatFeatures(bc, grayscale, saturation, 0,
                            size * i / numStripes, size, size / numStripes, size));
                    addAll(features, idxFeatures, step + 6, rectStatFeatures(bc, grayscale, saturation,
                            size * i / numStripes, 0, size / numStripes, size, size));
                    addAll(features, idxFeatures, step + 8, rectStatFeatures(bc, false, lightness, 0,
                            size * i / numStripes, size, size / numStripes, size));
                    addAll(features, idxFeatures, step + 10, rectStatFeatures(bc, false, lightness,
                            size * i / numStripes, 0, size / numStripes, size, size));
                }
                int grid = step == 1 ? grid1 : grid2;
                for (int i = 0; i < grid; i++) {
                    for (int j = 0; j < grid; j++) {
                        addAll(features, idxFeatures, step + 12, rectStatFeaturesAng(bc, ca, maxDist, grayscale, hue,
                                size * i / grid, size * j / grid, size / grid, size / grid, size));
                        addAll(features, idxFeatures, step + 14, rectStatFeatures(bc, false, lightness, size * i / grid,
                                size * j / grid, size / grid, size / grid, size));
                        addAll(features, idxFeatures, step + 16, rectStatFeatures(bc, grayscale, saturation,
                                size * i / grid, size * j / grid, size / grid, size / grid, size));
                    }

                    if (i > 0 && i < grid - 1) {
                        int dx = size * i / grid / 2;
                        int dy = size * i / grid / 2;
                        addAll(features, idxFeatures, step + 18, rectStatFeaturesAng(bc, ca, maxDist, grayscale, hue,
                                dx, dy, size - 2 * dx, size - 2 * dy, size));
                        addAll(features, idxFeatures, step + 20,
                                rectStatFeatures(bc, false, lightness, dx, dy, size - 2 * dx, size - 2 * dy, size));
                        addAll(features, idxFeatures, step + 22, rectStatFeatures(bc, grayscale, saturation, dx, dy,
                                size - 2 * dx, size - 2 * dy, size));
                    }
                }
                grid = step == 1 ? grid3 : grid4;
                for (int i = 0; i < grid; i++) {
                    for (int j = 0; j < grid; j++) {
                        features.addAll(rectBinCount(grayscale, hue, size * i / grid, size * j / grid, size / grid,
                                size / grid, size));
                    }
                }
                int m = size / grid;
                int[] dif1 = new int[m * size];
                int[] dif2 = new int[m * size];
                Arrays.fill(dif1, -1);
                Arrays.fill(dif2, -1);
                if (!grayscale) {
                    int center = grid / 2 * m;
                    int pos = 0;
                    for (int y = 0; y < size; y++) {
                        int off = y * size;
                        for (int x = 0; x < m; x++, off++, pos++) {
                            int h0 = hue[off + center];
                            if (h0 < 0)
                                continue;
                            int h1 = hue[off + center + m];
                            int h2 = hue[off + center - m];
                            if (h1 >= 0)
                                dif1[pos] = hDist(h0, h1);
                            if (h2 >= 0)
                                dif2[pos] = hDist(h0, h2);
                        }
                    }
                }
                features.addAll(rectStatFeatures(bc, grayscale, dif1, 0, 0, dif1.length, 1, dif1.length));
                features.addAll(rectStatFeatures(bc, grayscale, dif2, 0, 0, dif2.length, 1, dif2.length));
            }
            for (int idx : idxFeatures.keySet()) {
                features.addAll(stats(idxFeatures.get(idx)));
            }

            features.addAll(rectPosBin(grayscale, hue, 0, 0, size, size, size));
            features.addAll(rectPosBin(grayscale, saturation, 0, 0, size, size, size));
            features.addAll(rectPosBin(false, lightness, 0, 0, size, size, size));
            for (int i = 0; i < grid5; i++) {
                for (int j = 0; j < grid5; j++) {
                    features.addAll(rectPosBin(grayscale, hue, size * i / grid5, size * j / grid5, size / grid5,
                            size / grid5, size));
                    features.addAll(rectPosBin(grayscale, saturation, size * i / grid5, size * j / grid5, size / grid5,
                            size / grid5, size));
                    features.addAll(rectPosBin(false, lightness, size * i / grid5, size * j / grid5, size / grid5,
                            size / grid5, size));
                }
            }

            int[] eh = new int[size * size];
            int[] ev = new int[size * size];
            int[] edge = new int[size * size];
            int[] eang = new int[size * size];
            Arrays.fill(eh, -1);
            Arrays.fill(ev, -1);
            Arrays.fill(edge, -1);
            Arrays.fill(eang, -1);
            for (int y = 1; y < size - 1; y++) {
                int off = y * size + 1;
                for (int x = 1; x < size - 1; x++, off++) {
                    /* p1 p4 p6
                     * p2    p7
                     * p3 p5 p8 */
                    int p1 = gray[off - 1 - size];
                    int p2 = gray[off - 1];
                    int p3 = gray[off - 1 + size];
                    int p4 = gray[off - size];
                    int p5 = gray[off + size];
                    int p6 = gray[off + 1 - size];
                    int p7 = gray[off + 1];
                    int p8 = gray[off + 1 + size];
                    if (p1 < 0 || p3 < 0 || p6 < 0 || p8 < 0)
                        continue;
                    int vv = p1 + 2 * p4 + p6 - p3 - 2 * p5 - p8;
                    int hh = p1 + 2 * p2 + p3 - p6 - 2 * p7 - p8;
                    int vert = ev[off] = Math.min(999, Math.abs(vv));
                    int horiz = eh[off] = Math.min(999, Math.abs(hh));
                    edge[off] = sqrt[(vert << 10) | horiz];
                    eang[off] = atan[((vv + 1024) << 11) | (hh + 1024)];
                }
            }

            int[] gde = new int[] { 1, 3, 7 };
            for (int grid1 : gde) {
                for (int grid2 : gde) {
                    for (int i = 0; i < grid1; i++) {
                        for (int j = 0; j < grid2; j++) {
                            features.addAll(rectStatFeatures(bc, false, ev, size * i / grid1, size * j / grid2,
                                    size / grid1, size / grid2, size));
                            features.addAll(rectStatFeatures(bc, false, eh, size * i / grid1, size * j / grid2,
                                    size / grid1, size / grid2, size));
                            features.addAll(rectStatFeatures(bc, false, edge, size * i / grid1, size * j / grid2,
                                    size / grid1, size / grid2, size));
                            features.addAll(rectStatFeaturesAng(bc, ca, maxDist, false, eang, size * i / grid1,
                                    size * j / grid2, size / grid1, size / grid2, size));
                        }
                    }
                }
            }
            features.addAll(symmetries(red, green, blue));

            int[] gds = new int[] { 2, 6 };
            for (int grid1 : gds) {
                for (int grid2 : gds) {
                    for (int i = 0; i < grid1; i++) {
                        for (int j = 0; j < grid2; j++) {
                            features.addAll(rectCumulativeBinCount(hue, size * i / grid1, size * j / grid2,
                                    size / grid1, size / grid2, size));
                            features.addAll(rectCumulativeBinCount(lightness, size * i / grid1, size * j / grid2,
                                    size / grid1, size / grid2, size));
                            features.addAll(rectCumulativeBinCount(edge, size * i / grid1, size * j / grid2,
                                    size / grid1, size / grid2, size));
                        }
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        List<Float> ret = new ArrayList<Float>(numFeatures);
        for (int idx : bestFeatures) {
            ret.add(features.get(idx));
        }
        return ret;
    }

    private static List<Float> symmetries(int[] r, int[] g, int[] b) {
        List<Float> l = new ArrayList<Float>(4);
        float f0 = 0;
        float f1 = 0;
        float f2 = 0;
        float f3 = 0;
        int cnt = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int ri = r[i];
                if (ri < 0)
                    continue;
                int gi = g[i];
                int bi = b[i];

                int j = y * size + (size - 1 - x);
                int rj = r[j];
                if (rj < 0)
                    continue;
                int gj = g[j];
                int bj = b[j];

                int k = (size - 1 - y) * size + (size - 1 - x);
                int rk = r[k];
                if (rk < 0)
                    continue;
                int gk = g[k];
                int bk = b[k];

                int m = (size - 1 - y) * size + x;
                int rm = r[m];
                if (rm < 0)
                    continue;
                int gm = g[m];
                int bm = b[m];

                f0 += rgbDist(ri, gi, bi, rj, gj, bj);
                f1 += rgbDist(ri, gi, bi, rk, gk, bk);
                f2 += rgbDist(ri, gi, bi, rm, gm, bm);
                f3 += rgbDist(rj, gj, bj, rm, gm, bm);
                cnt++;
            }
        }
        if (cnt == 0) {
            for (int i = 0; i < 4; i++) {
                l.add(-1f);
            }
        } else {
            l.add(f0 / cnt);
            l.add(f1 / cnt);
            l.add(f2 / cnt);
            l.add(f3 / cnt);
        }
        return l;
    }

    private static void addAll(List<Float> features, Map<Integer, List<Float>> idxFeatures, int idx, List<Float> vals) {
        features.addAll(vals);
        if (idxFeatures != null) {
            int pos = 10 * idx;
            for (int i = 0; i < vals.size(); i++, pos++) {
                List<Float> l = idxFeatures.get(pos);
                if (l == null)
                    idxFeatures.put(pos, l = new ArrayList<Float>(1));
                l.add(vals.get(i));
            }
        }
    }

    private static List<Float> stats(List<Float> a) {
        List<Float> l = new ArrayList<Float>(2);
        double sum = 0;
        double sumSquares = 0;
        int cnt = a.size();
        for (float p : a) {
            sum += p;
            sumSquares += p * p;
        }
        l.add((float) ((sumSquares - sum * sum / cnt) / cnt));
        l.add((float) (sum / cnt));
        return l;
    }

    private static List<Float> rectStatFeatures(short[] bc, boolean ignore, int[] a, int rx, int ry, int rw, int rh,
            int width) {
        List<Float> l = new ArrayList<Float>(7);
        if (ignore) {
            for (int i = 0; i < 7; i++) {
                l.add(INF);
            }
        } else {
            Arrays.fill(bc, (short) 0);
            double sum = 0;
            double sumSquares = 0;
            double sumCubes = 0;
            int cnt = 0;
            double sx = 0;
            double sy = 0;
            int o0 = ry * width + rx;
            for (int y = ry; y < ry + rh; y++, o0 += width) {
                int off = o0;
                int px = a[off];
                for (int x = 0; x < rw; x++, off++) {
                    int p = a[off];
                    if (p < 0)
                        continue;
                    sx += Math.abs(p - px);
                    px = p;
                    if (y > ry) {
                        int pa = a[off - width];
                        if (pa >= 0)
                            sy += Math.abs(p - pa);
                    }
                    bc[p / binSize]++;
                    sum += p;
                    int pp = p * p;
                    sumSquares += pp;
                    sumCubes += pp * p;
                    cnt++;
                }
            }
            if (cnt == 0) {
                for (int i = 0; i < 7; i++) {
                    l.add(INF);
                }
            } else {
                double k3 = (sumCubes - 3 * sumSquares * sum / cnt + 2 * sum * sum * sum / cnt / cnt) / cnt;
                double k2 = (sumSquares - sum * sum / cnt) / cnt;
                int max = 0;
                double mode = 0;
                for (int i = 0; i < bc.length; i++) {
                    if (bc[i] > max) {
                        max = bc[i];
                        mode = i;
                    }
                }
                l.add((float) (sum / cnt));
                l.add((float) (k2));
                l.add(k2 == 0 ? 0 : (float) (k3 / Math.pow(k2, 1.5)));
                l.add((float) mode);
                l.add((float) (max / (double) cnt));
                l.add((float) (sx / cnt));
                l.add((float) (sy / cnt));
            }
        }
        return l;
    }

    private static List<Float> rectStatFeaturesAng(short[] bc, short[] ca, int[] maxDist, boolean ignore, int[] a,
            int rx, int ry, int rw, int rh, int width) {
        List<Float> l = new ArrayList<Float>(6);
        if (ignore) {
            for (int i = 0; i < 6; i++) {
                l.add(INF);
            }
        } else {
            Arrays.fill(ca, (short) 0);
            Arrays.fill(bc, (short) 0);
            int cnt = 0;
            double sx = 0;
            double sy = 0;
            int o0 = ry * width + rx;
            for (int y = ry; y < ry + rh; y++, o0 += width) {
                int off = o0;
                int px = a[off];
                for (int x = 0; x < rw; x++, off++) {
                    int p = a[off];
                    if (p < 0)
                        continue;
                    ca[p >>> 2]++;
                    cnt++;
                    sx += dist(p, px, 1000);
                    px = p;
                    if (y > ry) {
                        int pa = a[off - width];
                        if (pa >= 0)
                            sy += dist(p, pa, 1000);
                    }
                    bc[p / binSize]++;
                }
            }
            if (cnt == 0) {
                for (int i = 0; i < 6; i++) {
                    l.add(INF);
                }
            } else {
                int[] sum = new int[50];
                for (int i = 0; i < ca.length; i++) {
                    int cai = ca[i];
                    if (cai > 0) {
                        for (int j = 0; j < sum.length; j++) {
                            sum[j] += dist((i << 2) + 1, j * 20, 1000) * cai;
                        }
                    }
                }
                int avg = 0;
                int min = Integer.MAX_VALUE;
                for (int i = 0; i < sum.length; i++) {
                    int si = sum[i];
                    if (si < min) {
                        min = si;
                        avg = i * 20 + 10;
                    }
                }
                double var = 0;
                for (int i = 0; i < ca.length; i++) {
                    int cai = ca[i];
                    if (cai > 0) {
                        double d = dist((i << 2) + 1, avg, 1000);
                        var += cai * d * d;
                    }
                }
                int max = 0;
                double mode = 0;
                for (int i = 0; i < bc.length; i++) {
                    if (bc[i] > max) {
                        max = bc[i];
                        mode = i;
                    }
                }
                l.add((float) avg);
                l.add((float) (var / cnt));
                l.add((float) mode);
                l.add((float) (max / (double) cnt));
                l.add((float) (sx / cnt));
                l.add((float) (sy / cnt));
            }
        }
        return l;
    }

    private static final int dist(int a, int b, int max) {
        if (b < a)
            return dist(b, a, max);
        return Math.min(b - a, a + max - b);
    }

    private static List<Float> rectBinCount(boolean ignore, int[] a, int rx, int ry, int rw, int rh, int width) {
        int binSizeCount = 34;
        int[] bc = new int[(1000 + binSizeCount - 1) / binSizeCount];
        List<Float> l = new ArrayList<Float>(bc.length);
        int cnt = 0;
        if (!ignore) {
            int o0 = ry * width + rx;
            for (int y = ry; y < ry + rh; y++, o0 += width) {
                int off = o0;
                for (int x = 0; x < rw; x++, off++) {
                    int p = a[off];
                    if (p < 0)
                        continue;
                    bc[p / binSizeCount]++;
                    cnt++;
                }
            }
        }
        if (cnt == 0) {
            for (int i = 0; i < bc.length; i++) {
                l.add(INF);
            }
        } else {
            float div = (float) cnt;
            for (int i = 0; i < bc.length; i++) {
                l.add(bc[i] / div);
            }
        }
        return l;
    }

    private static List<Float> rectCumulativeBinCount(int[] a, int rx, int ry, int rw, int rh, int width) {
        int binSize = 100;
        int[] bc = new int[(1000 + binSize - 1) / binSize];
        List<Float> l = new ArrayList<Float>(bc.length);
        int cnt = 0;
        int o0 = ry * width + rx;
        for (int y = ry; y < ry + rh; y++, o0 += width) {
            int off = o0;
            for (int x = 0; x < rw; x++, off++) {
                int p = a[off];
                if (p < 0)
                    continue;
                bc[p / binSize]++;
                cnt++;
            }
        }
        if (cnt == 0) {
            for (int i = 0; i < bc.length - 1; i++) {
                l.add(INF);
            }
        } else {
            float div = (float) cnt;
            int tot = 0;
            for (int i = 0; i < bc.length - 1; i++) {
                tot += bc[i];
                l.add(tot / div);
            }
        }
        return l;
    }

    private static List<Float> rectPosBin(boolean ignore, int[] a, int rx, int ry, int rw, int rh, int width) {
        int binSize = 50;
        int len = (1000 + binSize - 1) / binSize;
        List<Float> l = new ArrayList<Float>(len * 2);
        if (ignore) {
            for (int i = 0; i < len * 2; i++) {
                l.add(INF);
            }
        } else {
            int[] xc = new int[len];
            int[] yc = new int[len];
            int[] bc = new int[len];
            int o0 = ry * width + rx;
            for (int y = ry; y < ry + rh; y++, o0 += width) {
                int off = o0;
                for (int x = rx; x < rw; x++, off++) {
                    int p = a[off];
                    if (p < 0)
                        continue;
                    int bin = p / binSize;
                    bc[bin]++;
                    xc[bin] += x;
                    yc[bin] += y;
                }
            }
            for (int i = 0; i < len; i++) {
                float cnt = bc[i];
                l.add(cnt == 0 ? INF : xc[i] / cnt);
                l.add(cnt == 0 ? INF : yc[i] / cnt);
            }
        }
        return l;
    }

    private static int[][] getChannels(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] rgb = new int[w * h];
        img.getRGB(0, 0, w, h, rgb, 0, w);

        int len = size * size;
        int[] hue = new int[len];
        int[] sat = new int[len];
        int[] light = new int[len];
        int[] gray = new int[len];
        int[] r = new int[len];
        int[] g = new int[len];
        int[] b = new int[len];
        Arrays.fill(hue, -1);
        Arrays.fill(sat, -1);
        Arrays.fill(light, -1);
        Arrays.fill(r, -1);
        Arrays.fill(g, -1);
        Arrays.fill(b, -1);
        Arrays.fill(gray, -1);

        int oy = 0;
        int ox = 0;
        if (w >= h)
            oy = (size - h) / 2;
        else
            ox = (size - w) / 2;

        boolean grayscale = true;
        float[] hsl = new float[3];
        for (int y1 = 0; y1 < h; y1++) {
            int pos1 = y1 * w;
            int pos2 = (y1 + oy) * size + ox;
            for (int x1 = 0; x1 < w; x1++, pos1++, pos2++) {
                int pixel = rgb[pos1];
                int rr = r[pos2] = r(pixel);
                int gg = g[pos2] = g(pixel);
                int bb = b[pos2] = b(pixel);
                if (grayscale && (rr != gg || rr != bb))
                    grayscale = false;
                gray[pos2] = (rr * 299 + gg * 587 + bb * 114) / 1000;
                RGBtoHSB(rr, gg, bb, hsl);
                hue[pos2] = (((int) Math.round(hsl[0] * 999)) + 500) % 1000;
                sat[pos2] = (int) Math.round(hsl[1] * 999);
                light[pos2] = (int) Math.round(hsl[2] * 999);
            }
        }
        if (grayscale) {
            Arrays.fill(hue, -1);
            Arrays.fill(sat, -1);
        }
        return new int[][] { hue, sat, light, gray, r, g, b, grayscale ? new int[0] : null };
    }

    private static void RGBtoHSB(int r, int g, int b, float[] hsbvals) {
        float hue, saturation, brightness;
        int cmax = (r > g) ? r : g;
        if (b > cmax)
            cmax = b;
        int cmin = (r < g) ? r : g;
        if (b < cmin)
            cmin = b;

        brightness = ((float) cmax) / 255.0f;
        if (cmax != 0)
            saturation = ((float) (cmax - cmin)) / ((float) cmax);
        else
            saturation = 0;
        if (saturation == 0)
            hue = 0;
        else {
            float redc = ((float) (cmax - r)) / ((float) (cmax - cmin));
            float greenc = ((float) (cmax - g)) / ((float) (cmax - cmin));
            float bluec = ((float) (cmax - b)) / ((float) (cmax - cmin));
            if (r == cmax)
                hue = bluec - greenc;
            else if (g == cmax)
                hue = 2.0f + redc - bluec;
            else
                hue = 4.0f + greenc - redc;
            hue = hue / 6.0f;
            if (hue < 0)
                hue = hue + 1.0f;
        }
        hsbvals[0] = hue;
        hsbvals[1] = saturation;
        hsbvals[2] = brightness;
    }

    private static BufferedImage resizeImage(BufferedImage img) {
        int w1 = img.getWidth();
        int h1 = img.getHeight();
        if (w1 == 0 || h1 == 0)
            return null;
        int w2 = w1;
        int h2 = h1;
        if (w1 >= h1) {
            w2 = size;
            h2 = size * h1 / w1;
        } else {
            h2 = size;
            w2 = size * w1 / h1;
        }
        if (w2 == 0 || h2 == 0)
            return null;
        final BufferedImage resized = new BufferedImage(w2, h2, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(img, 0, 0, w2, h2, null);
        g2.dispose();
        return resized;
    }

    private static final int hDist(int h1, int h2) {
        if (h2 > h1)
            return hDist(h2, h1);
        return Math.min(h1 - h2, 1000 + h2 - h1);
    }

    private static final int rgbDist(int r1, int g1, int b1, int r2, int g2, int b2) {
        return sq(r1 - r2) + sq(g1 - g2) + sq(b1 - b2);
    }

    private static final int r(int rgb) {
        return (rgb >>> 16) & 255;
    }

    private static final int g(int rgb) {
        return (rgb >>> 8) & 255;
    }

    private static final int b(int rgb) {
        return rgb & 255;
    }

    private static final int sq(int x) {
        return x * x;
    }
}