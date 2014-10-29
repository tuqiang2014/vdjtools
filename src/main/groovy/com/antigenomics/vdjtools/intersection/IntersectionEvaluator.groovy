package com.antigenomics.vdjtools.intersection

import com.antigenomics.vdjtools.basic.SegmentUsage
import com.antigenomics.vdjtools.basic.Spectratype
import com.antigenomics.vdjtools.join.JointSample
import com.antigenomics.vdjtools.sample.Sample
import com.antigenomics.vdjtools.util.ExecUtil
import com.antigenomics.vdjtools.util.MathUtil
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import sun.reflect.generics.reflectiveObjects.NotImplementedException

/**
 * A helper class to compute various intersection metrics for joint intersection
 */
class IntersectionEvaluator {
    public static boolean VERBOSE = true

    private final JointSample jointSample
    private SegmentUsage segmentUsageCache
    private final Spectratype[] spectratypeCache
    private final Map<String, Double> metricCache = new HashMap<>()

    IntersectionEvaluator(JointSample jointSample) {
        this.jointSample = jointSample
        this.spectratypeCache = new Spectratype[jointSample.numberOfSamples]
    }

    private Spectratype getSpectratype(int sampleIndex) {
        if (!spectratypeCache[sampleIndex]) {
            spectratypeCache[sampleIndex] = new Spectratype(jointSample.getSample(sampleIndex),
                    jointSample.intersectionType,
                    false)
        }
        spectratypeCache[sampleIndex]
    }

    private SegmentUsage getSegmentUsage() {
        if (!segmentUsageCache) {
            segmentUsageCache = new SegmentUsage((0..<jointSample.numberOfSamples).collect {
                jointSample.getSample(it)
            } as Sample[], false)
        }
        segmentUsageCache
    }

    // all metrics are [0, +inf] with 0 for equal samples and symmetric
    private double _computeIntersectionMetric(IntersectMetric metric,
                                              int i, int j) {
        ExecUtil.report(this, "Computing $metric", VERBOSE)
        switch (metric) {
            case IntersectMetric.Diversity:
                def div1 = jointSample.getSample(i).diversity,
                        div2 = jointSample.getSample(j).diversity,
                        div12 = jointSample.getIntersectionDiv(i, j)
                return -Math.log10(div12 / Math.sqrt(div1 * div2) + 1e-9)

            case IntersectMetric.Frequency:
                return -Math.log10(Math.sqrt(jointSample.getIntersectionFreq(i, j) * jointSample.getIntersectionFreq(j, i)) + 1e-9)

            case IntersectMetric.Frequency2:
                double F2 = 0;
                jointSample.each {
                    F2 += Math.sqrt(it.getFreq(i) * it.getFreq(j))
                }
                return -Math.log10(F2 + 1e-9)

            case IntersectMetric.Correlation:
                double R = Double.NaN

                int n = jointSample.getIntersectionDiv(i, j)

                if (n > 2) {
                    def x = new double[n],
                        y = new double[n]
                    int k = 0
                    jointSample.each {
                        if (it.present(i) && it.present(j)) {
                            x[k] = it.getFreq(i)
                            y[k] = it.getFreq(j)
                        }
                        k++
                    }

                    R = new PearsonsCorrelation().correlation(x, y)
                }
                return (1 - R) / 2.0 // negative distance values are prohibited

            case IntersectMetric.vJSD:
                return MathUtil.JSD(
                        segmentUsage.vUsageVector(0),
                        segmentUsage.vUsageVector(1))

            case IntersectMetric.vjJSD:
                return MathUtil.JSD(
                        [segmentUsage.vUsageVector(0).collect(), segmentUsage.jUsageVector(0).collect()].flatten() as double[],
                        [segmentUsage.vUsageVector(1).collect(), segmentUsage.jUsageVector(1).collect()].flatten() as double[])

            case IntersectMetric.vj2JSD:
                return MathUtil.JSD(
                        segmentUsage.vjUsageMatrix(0).collect().flatten() as double[],
                        segmentUsage.vjUsageMatrix(1).collect().flatten() as double[])

            case IntersectMetric.sJSD:
                return MathUtil.JSD(getSpectratype(i).histogram,
                        getSpectratype(j).histogram)

            default:
                throw new NotImplementedException()
        }
    }


    public double computeIntersectionMetric(IntersectMetric metric,
                                            int i, int j) {
        def key = [metric.shortName, i, j].join("_")
        def value = metricCache[key]
        if (!value)
            metricCache.put(key, value = _computeIntersectionMetric(metric, i, j))
        value
    }

    public double computeIntersectionMetric(IntersectMetric intersectMetric) {
        computeIntersectionMetric(intersectMetric, 0, 1)
    }
}