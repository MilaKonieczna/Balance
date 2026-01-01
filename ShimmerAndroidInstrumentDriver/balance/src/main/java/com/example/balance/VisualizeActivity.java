package com.example.balance;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.Toast;

import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.SizeLayoutType;
import com.androidplot.ui.SizeMetric;
import com.androidplot.ui.widget.DomainLabelWidget;
import com.androidplot.ui.widget.RangeLabelWidget;
import com.androidplot.ui.widget.TitleWidget;
import com.androidplot.xy.XLayoutStyle;
import com.androidplot.xy.XYLegendWidget;
import com.androidplot.xy.FillDirection;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.series.XYSeries;
import com.androidplot.xy.YLayoutStyle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VisualizeActivity extends AppCompatActivity {
    private File folder;
    private XYPlot plot;

    private static final int[] colors = new int[] {
            Color.rgb(31, 119, 180),
            Color.rgb(255, 127, 14),
            Color.rgb(44, 160, 44),
            Color.rgb(214, 39, 40),
            Color.rgb(148, 103, 189),
            Color.rgb(140, 86, 75),
            Color.rgb(227, 119, 194),
            Color.rgb(127, 127, 127),
            Color.rgb(188, 189, 34),
            Color.rgb(23, 190, 207)
    };


    private final List<Double> timestamps = new ArrayList<>();
    private final List<String> headers = new ArrayList<>();
    private final List<List<Double>> values = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);

        folder = new File(getExternalFilesDir(null), "Balance");
        if (!folder.exists()) folder.mkdirs();

        openFile();

        Button diff = findViewById(R.id.diffFile);
        diff.setOnClickListener(v->openFile());

        //setting the plot design
        plot = findViewById(R.id.plot);

        plot.setPlotPadding(10, 10, 20, 20);
        plot.setPlotMargins(10, 10, 20, 20);

        XYGraphWidget graph = plot.getGraphWidget();
        graph.getBackgroundPaint().setColor(Color.WHITE);
        graph.getGridBackgroundPaint().setColor(Color.WHITE);

        plot.getBackgroundPaint().setColor(Color.WHITE);
        graph.setMarginBottom(80);

        Paint gridLinePaint = new Paint();
        gridLinePaint.setColor(Color.LTGRAY);
        gridLinePaint.setStrokeWidth(1);
        graph.setGridDomainLinePaint(gridLinePaint);
        graph.setGridRangeLinePaint(gridLinePaint);

        if (graph.getDomainLabelPaint() == null)
            graph.setDomainLabelPaint(new Paint());
        graph.getDomainLabelPaint().setColor(Color.DKGRAY);
        graph.getDomainLabelPaint().setTextSize(15);

        if (graph.getRangeLabelPaint() == null)
            graph.setRangeLabelPaint(new Paint());
        graph.getRangeLabelPaint().setColor(Color.DKGRAY);
        graph.getRangeLabelPaint().setTextSize(15);

        TitleWidget title = plot.getTitleWidget();

        if (title.getLabelPaint() == null)
            title.setLabelPaint(new Paint());
        if (title.getBackgroundPaint() == null)
            title.setBackgroundPaint(new Paint());

        title.getLabelPaint().setColor(Color.DKGRAY);
        title.getBackgroundPaint().setColor(Color.WHITE);

        plot.setTitle("Measurement");
        title.getWidthMetric().setValue(300);
        title.getHeightMetric().setValue(25);
        title.getLabelPaint().setTextSize(25);

        DomainLabelWidget domain = plot.getDomainLabelWidget();
        if (domain.getBackgroundPaint() == null) domain.setBackgroundPaint(new Paint());
        if (domain.getLabelPaint() == null) domain.setLabelPaint(new Paint());

        domain.getBackgroundPaint().setColor(Color.WHITE);
        domain.getLabelPaint().setColor(Color.DKGRAY);
        domain.getLabelPaint().setTextSize(18);
        domain.getWidthMetric().setValue(150);
        domain.getHeightMetric().setValue(25);
        domain.getLabelPaint().setTextSize(23);
        plot.setDomainLabel("Time");

        plot.getLayoutManager().position(
                domain,
                0, XLayoutStyle.ABSOLUTE_FROM_CENTER,
                50, YLayoutStyle.ABSOLUTE_FROM_BOTTOM,
                AnchorPosition.BOTTOM_MIDDLE
        );

        RangeLabelWidget range = plot.getRangeLabelWidget();
        if (range.getBackgroundPaint() == null) range.setBackgroundPaint(new Paint());
        if (range.getLabelPaint() == null) range.setLabelPaint(new Paint());

        range.getBackgroundPaint().setColor(Color.WHITE);
        range.getLabelPaint().setColor(Color.DKGRAY);
        range.getLabelPaint().setTextSize(18);
        range.getWidthMetric().setValue(25);
        range.getHeightMetric().setValue(150);
        range.getLabelPaint().setTextSize(23);
        plot.setRangeLabel("Value");


        XYLegendWidget legend = plot.getLegendWidget();
        if (legend.getBackgroundPaint() == null) legend.setBackgroundPaint(new Paint());
        legend.getBackgroundPaint().setColor(Color.WHITE);

        legend.getTextPaint().setTextSize(23);
        legend.getIconSizeMetrics().setWidthMetric(
                new SizeMetric(20, SizeLayoutType.ABSOLUTE));
        legend.getIconSizeMetrics().setHeightMetric(
                new SizeMetric(20, SizeLayoutType.ABSOLUTE));

        plot.getLayoutManager().position(
                legend,
                0, XLayoutStyle.ABSOLUTE_FROM_CENTER,
                0, YLayoutStyle.ABSOLUTE_FROM_BOTTOM,
                AnchorPosition.BOTTOM_MIDDLE
        );

        legend.getTextPaint().setColor(Color.DKGRAY);

        plot.disableAllMarkup();
    }

    public void back(View v) {
        startActivity(new Intent(this, CollectActivity.class));
    }


    public void openFile() {

        File[] files = folder.listFiles();
        if (files == null) {
            Toast.makeText(this, "No CSV files", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> csvFiles = new ArrayList<>();
        for (File f : files) {
            if (f.getName().endsWith(".csv")) {
                csvFiles.add(f.getName());
            }
        }
        Collections.sort(csvFiles, (s1, s2) -> {
            String str1 = s1.replaceAll("\\D", "");
            String str2 = s2.replaceAll("\\D", "");
            if (!str1.isEmpty() && !str2.isEmpty()) {
                return Integer.compare(Integer.parseInt(str2), Integer.parseInt(str1));
            }
            return s2.compareTo(s1);
        });

        if (csvFiles.isEmpty()) {
            Toast.makeText(this, "No CSV files", Toast.LENGTH_SHORT).show();
            return;
        }

        View popupView = getLayoutInflater().inflate(R.layout.dialog_files, null);
        ListView listView = popupView.findViewById(R.id.file_list);
        EditText searchBar = popupView.findViewById(R.id.search_bar);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, R.layout.item, R.id.filename, new ArrayList<>(csvFiles)) {

            private final List<String> original = new ArrayList<>(csvFiles);

            @Override
            public Filter getFilter() {
                return new Filter() {

                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        List<String> filtered = new ArrayList<>();

                        if (constraint == null || constraint.length() == 0) {
                            filtered.addAll(original);
                        } else {
                            String query = constraint.toString()
                                    .toLowerCase()
                                    .replace(".csv", "")
                                    .trim();

                            for (String file : original) {
                                String normalized = file
                                        .toLowerCase()
                                        .replace(".csv", "");

                                if (normalized.contains(query)) {
                                    filtered.add(file);
                                }
                                else if (query.matches("\\d+") &&
                                        normalized.replaceAll("\\D", "").contains(query)) {
                                    filtered.add(file);
                                }
                            }
                        }

                        results.values = filtered;
                        results.count = filtered.size();
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        addAll((List<String>) results.values);
                        notifyDataSetChanged();
                    }
                };
            }
        };


        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(popupView)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.y = 30;
        dialog.getWindow().setAttributes(params);

        listView.setOnItemClickListener((p, view, pos, id) -> {
            File selected = new File(folder, adapter.getItem(pos));
            parseCsv(selected);
            showSignalPicker();
            dialog.dismiss();
        });


        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.getFilter().filter(s);
            }
        });
    }

    private void parseCsv(File file) {
        timestamps.clear();
        headers.clear();
        values.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.readLine();
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headerParts = headerLine.split(",");
            int expectedColumns = headerParts.length;

            for (int i = 1; i < expectedColumns; i++) {
                headers.add(headerParts[i].trim());
                values.add(new ArrayList<>());
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");

                if (parts.length < expectedColumns) {
                    continue;
                }

                try {
                    double t = Double.parseDouble(parts[0]);

                    List<Double> rowValues = new ArrayList<>();
                    for (int i = 1; i < expectedColumns; i++) {
                        rowValues.add(Double.parseDouble(parts[i]));
                    }

                    timestamps.add(t);
                    for (int i = 0; i < rowValues.size(); i++) {
                        values.get(i).add(rowValues.get(i));
                    }
                } catch (NumberFormatException e) {
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void showSignalPicker() {
        String[] names = headers.toArray(new String[0]);
        boolean[] checked = new boolean[names.length];

        new AlertDialog.Builder(this)
                .setTitle("Select signals")
                .setMultiChoiceItems(names, checked,
                        (d, i, isChecked) -> checked[i] = isChecked)
                .setPositiveButton("Plot",
                        (d, w) -> plotSignals(checked))
                .show();
    }


    private void plotSignals(boolean[] checked) {
        plot.clear();

        for (int i = 0; i < checked.length; i++) {
            if (!checked[i]) continue;

            XYSeries series = new SimpleXYSeries(
                    timestamps,
                    values.get(i),
                    headers.get(i)
            );

            int color = colors[i % colors.length];
            LineAndPointFormatter fmt =
                    new LineAndPointFormatter(color, null, null, null);

            fmt.setFillDirection(FillDirection.BOTTOM);
            plot.addSeries(series, fmt);
        }

        plot.redraw();
    }
}
