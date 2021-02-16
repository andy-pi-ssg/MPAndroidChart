package com.github.mikephil.charting.highlight;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.BarLineScatterCandleBubbleData;
import com.github.mikephil.charting.data.ChartData;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.interfaces.dataprovider.BarLineScatterCandleBubbleDataProvider;
import com.github.mikephil.charting.interfaces.dataprovider.CombinedDataProvider;
import com.github.mikephil.charting.interfaces.datasets.IDataSet;
import com.github.mikephil.charting.utils.MPPointD;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Philipp Jahoda on 21/07/15.
 */
public class ChartHighlighter<T extends BarLineScatterCandleBubbleDataProvider> implements IHighlighter
{
    /**
     This value is based on the Apple HMI recommandation : minimum 'interactive' target size of 44 x 44px, so a 22px radius circle around the tap point
     */
    private Float minimum_target_size = 44f;

    /**
     The minimum distance between a tap location and a trigger point
     This value is based on the Apple HMI recommandation (minimum 'interactive' target size of 44 x 44px, so a 22px radius circle around the tap point)
     */
    private Float minimum_radius_size = 22f;

    /**
     * instance of the data-provider
     */
    protected T mChart;

    /**
     * buffer for storing previously highlighted values
     */
    protected List<Highlight> mHighlightBuffer = new ArrayList<Highlight>();

    public ChartHighlighter(T chart) {
        this.mChart = chart;
    }

    @Override
    public Highlight getHighlight(float x, float y) {

        MPPointD pos = getValsForTouch(x, y);
        float xVal = (float) pos.x;
        MPPointD.recycleInstance(pos);

        Highlight high = getHighlightForX(xVal, x, y);
        return high;
    }

    /**
     * Returns a recyclable MPPointD instance.
     * Returns the corresponding xPos for a given touch-position in pixels.
     *
     * @param x
     * @param y
     * @return
     */
    protected MPPointD getValsForTouch(float x, float y) {

        // take any transformer to determine the x-axis value
        MPPointD pos = mChart.getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y);
        return pos;
    }

    /**
     * Returns the corresponding Highlight for a given xVal and x- and y-touch position in pixels.
     *
     * @param xVal
     * @param x
     * @param y
     * @return
     */
    protected Highlight getHighlightForX(float xVal, float x, float y) {

        List<Highlight> closestValues = getHighlightsAtXValue(xVal, x, y);

        if(closestValues.isEmpty()) {
            return null;
        }

        float leftAxisMinDist = getMinimumDistance(closestValues, y, YAxis.AxisDependency.LEFT);
        float rightAxisMinDist = getMinimumDistance(closestValues, y, YAxis.AxisDependency.RIGHT);

        YAxis.AxisDependency axis = leftAxisMinDist < rightAxisMinDist ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT;

        Highlight detail = getClosestHighlightByPixel(closestValues, x, y, axis, mChart.getMaxHighlightDistance());

        return detail;
    }

    /**
     * Returns the minimum distance from a touch value (in pixels) to the
     * closest value (in pixels) that is displayed in the chart.
     *
     * @param closestValues
     * @param pos
     * @param axis
     * @return
     */
    protected float getMinimumDistance(List<Highlight> closestValues, float pos, YAxis.AxisDependency axis) {

        float distance = Float.MAX_VALUE;

        for (int i = 0; i < closestValues.size(); i++) {

            Highlight high = closestValues.get(i);

            if (high.getAxis() == axis) {

                float tempDistance = Math.abs(getHighlightPos(high) - pos);
                if (tempDistance < distance) {
                    distance = tempDistance;
                }
            }
        }

        return distance;
    }

    protected float getHighlightPos(Highlight h) {
        return h.getYPx();
    }

    /**
     * Returns a list of Highlight objects representing the entries closest to the given xVal.
     * The returned list contains two objects per DataSet (closest rounding up, closest rounding down).
     *
     * @param xVal the transformed x-value of the x-touch position
     * @param x    touch position
     * @param y    touch position
     * @return
     */
    protected List<Highlight> getHighlightsAtXValue(float xVal, float x, float y) {

        mHighlightBuffer.clear();

        BarLineScatterCandleBubbleData data = getData();

        if (data == null)
            return mHighlightBuffer;

        for (int i = 0, dataSetCount = data.getDataSetCount(); i < dataSetCount; i++) {

            IDataSet dataSet = data.getDataSetByIndex(i);

            // don't include DataSets that cannot be highlighted
            if (!dataSet.isHighlightEnabled())
                continue;

            mHighlightBuffer.addAll(buildHighlights(dataSet, i, xVal, DataSet.Rounding.CLOSEST));
        }

        return mHighlightBuffer;
    }

    /**
     * An array of `Highlight` objects corresponding to the selected xValue and dataSetIndex.
     *
     * @param set
     * @param dataSetIndex
     * @param xVal
     * @param rounding
     * @return
     */
    protected List<Highlight> buildHighlights(IDataSet set, int dataSetIndex, float xVal, DataSet.Rounding rounding) {

        ArrayList<Highlight> highlights = new ArrayList<>();

        //noinspection unchecked
        List<Entry> entries = set.getEntriesForXValue(xVal);
        if (entries.size() == 0) {
            // Try to find closest x-value and take all entries for that x-value
            final Entry closest = set.getEntryForXValue(xVal, Float.NaN, rounding);
            if (closest != null)
            {
                //noinspection unchecked
                entries = set.getEntriesForXValue(closest.getX());
            }
        }

        if (entries.size() == 0)
            return highlights;

        for (Entry e : entries) {
            MPPointD pixels = mChart.getTransformer(
                    set.getAxisDependency()).getPixelForValues(e.getX(), e.getY());

            highlights.add(new Highlight(
                    e.getX(), e.getY(),
                    (float) pixels.x, (float) pixels.y,
                    dataSetIndex, set.getAxisDependency()));
        }

        return highlights;
    }

    /**
     * Returns the Highlight of the DataSet that contains the closest value on the
     * y-axis.
     *
     * @param closestValues        contains two Highlight objects per DataSet closest to the selected x-position (determined by
     *                             rounding up an down)
     * @param x
     * @param y
     * @param axis                 the closest axis
     * @param minSelectionDistance
     * @return
     */
    public Highlight getClosestHighlightByPixel(List<Highlight> closestValues, float x, float y,
                                                YAxis.AxisDependency axis, float minSelectionDistance) {

        boolean distanceIsFromLineChart = false;
        Highlight closest = null;
        float distance = minSelectionDistance;
        ChartData highlighterChartData = null;

        // We need to known the step width to constrain the closest selection distance check on "Bar" chart data
        float stepWidth = mChart.getWidth()/mChart.getXRange();

        for (int i = 0; i < closestValues.size(); i++) {

            Highlight high = closestValues.get(i);

            if (axis == null || high.getAxis() == axis) {

                // 1. Compute the distance between the finger tap position and the chart origin coordinate
                float cDistance = getDistance(x, y, high.getXPx(), high.getYPx());

                // 1bis. Some checks are based on the highlighter related chart data.
                List<BarLineScatterCandleBubbleData> allData = ((CombinedDataProvider) mChart).getCombinedData().getAllData();
                //check if list contains index of high value
                if (high.getDataIndex() >=0 && high.getDataIndex() <= allData.size()-1) {
                    highlighterChartData = allData.get(high.getDataIndex());
                }


                // 2. depending on the chart data, there's some additional tests to pass
                if (highlighterChartData instanceof LineData) {
                    /*
                         We consider the chart line points have a clickable area around to.
                         Thus, the clickable area is based on a circle, with a radius based on the Apple HMI recommandation (arbitrary choice)
                         */

                    // 2a. test whether it's the first distance to take into account, or if the finger tap location is not too far from the chart line
                    // 2b. We replace the previous closest chart line distance only the previous one is from another chart type,
                    //     or if it's a smaller value
                    if (cDistance <= minimum_radius_size && (!distanceIsFromLineChart || cDistance < distance)) {
                        distanceIsFromLineChart = true;
                    } else {
                        continue;
                    }
                } else {
                    /*
                     Here are all other chart cases.
                     Current implementation is specific to the Bar Chart, without any thought to the others.
                     Thus, we consider the tap location must be "inside" the bar to be valid.
                     Moreover, the current closest distance must not be a line Chart : a line chart has a higher priority.
                     If you need to handle other cases in a different way, be free to update the code.
                     */

                    // 2a. Select the closest 'high'lighter
                    // However, a 'Line Chart Data' has a higher priority than any other chart data type
                    if (cDistance >= distance) {
                        continue;
                    }

                    // 2b. Get the bar bottom position
                    double barChartBottom = (double) (mChart.getYChartMin() + mChart.getHeight());

                    if (highlighterChartData instanceof BarData && mChart != null) {

                        if (!highlighterChartData.getDataSets().isEmpty()) {
                            BarDataSet dataSet = (BarDataSet) highlighterChartData.getDataSets().get(0);
                            float bottomValue = ((BarEntry) dataSet.getEntryForXValue(high.getX(), -1)).getYVals()[0];
                            MPPointD px = mChart.getTransformer(dataSet.getAxisDependency()).getPixelForValues(high.getX(), bottomValue);
                            barChartBottom = px.y;
                        }
                    }


                    // 2c. Check whether the tap location is inside the bar, and if the current nearest distance is not from a line chart point
                    if (!(x >= high.getXPx() - stepWidth
                            && x <= high.getXPx() + stepWidth
                            && y < barChartBottom
                            && !distanceIsFromLineChart)) {
                        continue;
                    }
                }


                // 4. This 'high'lighter becomes the closest one to the finger tap
                closest = high;
                distance = cDistance;


            }
        }

        return closest;
    }

    /**
     * Calculates the distance between the two given points.
     *
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @return
     */
    protected float getDistance(float x1, float y1, float x2, float y2) {
        //return Math.abs(y1 - y2);
        //return Math.abs(x1 - x2);
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    protected BarLineScatterCandleBubbleData getData() {
        return mChart.getData();
    }
}
