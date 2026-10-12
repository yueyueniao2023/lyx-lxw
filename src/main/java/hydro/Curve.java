package hydro;

import java.util.Arrays;

/** 在两个已知点之间做直线插值，用于水位—库容、流量—尾水位曲线。 */
public class Curve {
    private final double[] x;
    private final double[] y;

    public Curve(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("曲线至少需要两个点，且横纵坐标数量相同");
        }
        this.x = x.clone();
        this.y = y.clone();
        for (int i = 0; i < x.length; i++) {
            if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])
                    || (i > 0 && (x[i] <= x[i - 1] || y[i] < y[i - 1]))) {
                throw new IllegalArgumentException("曲线必须是有限数，横坐标递增，纵坐标不递减");
            }
        }
    }

    public double minX() { return x[0]; }
    public double maxX() { return x[x.length - 1]; }

    public double valueAt(double value) {
        if (!Double.isFinite(value) || value < minX() || value > maxX()) {
            throw new IllegalArgumentException("插值超出曲线范围：" + value);
        }
        int index = Arrays.binarySearch(x, value);
        if (index >= 0) return y[index];
        int right = -index - 1;
        int left = right - 1;
        double fraction = (value - x[left]) / (x[right] - x[left]);
        return y[left] + fraction * (y[right] - y[left]);
    }
}
