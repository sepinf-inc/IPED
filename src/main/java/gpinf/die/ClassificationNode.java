package gpinf.die;

public class ClassificationNode {
	ClassificationNode left, right;
	float splitVal;
	int classif, level, splitFeature, startRow, endRow, total;
	double impurity;

	public ClassificationNode(int classif, int total, double impurtity, int level, int startRow, int endRow) {
		this.classif = classif;
        this.total = total;
		this.startRow = startRow;
		this.endRow = endRow;
		this.level = level;
		this.impurity = impurtity;
	}

	public float getValue() {
		return classif / (float) total;
	}

	public boolean isLeaf() {
		return left == null && right == null;
	}

	public boolean isPure() {
		return classif == 0 || classif == total;
	}
}