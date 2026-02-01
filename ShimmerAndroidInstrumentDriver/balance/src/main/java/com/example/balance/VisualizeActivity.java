package com.example.balance;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.Toast;

import com.androidplot.ui.SizeLayoutType;
import com.androidplot.xy.*;
import com.androidplot.series.XYSeries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VisualizeActivity extends AppCompatActivity {
    private File folder;
    private XYPlot plot;

    private static final int[] colors = new int[]{
            Color.rgb(31, 119, 180), Color.rgb(255, 127, 14),
            Color.rgb(44, 160, 44), Color.rgb(214, 39, 40),
            Color.rgb(148, 103, 189), Color.rgb(140, 86, 75),
            Color.rgb(227, 119, 194), Color.rgb(127, 127, 127),
            Color.rgb(188, 189, 34), Color.rgb(23, 190, 207)
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

        plot = findViewById(R.id.plot);
        setupPlotStyle();

        openFile();

        Button diff = findViewById(R.id.diffFile);
        diff.setOnClickListener(v -> openFile());
    }

    public void back(View v) {

        finish();
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
            try {
                String str1 = s1.replaceAll("\\D", "");
                String str2 = s2.replaceAll("\\D", "");
                if (!str1.isEmpty() && !str2.isEmpty()) {
                    return Long.compare(Long.parseLong(str2), Long.parseLong(str1));
                }
            } catch (NumberFormatException e) {
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

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
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
                            String query = constraint.toString().toLowerCase().trim();
                            for (String file : original) {
                                if (file.toLowerCase().contains(query)) filtered.add(file);
                            }
                        }
                        results.values = filtered;
                        results.count = filtered.size();
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results.values != null) addAll((List<String>) results.values);
                        notifyDataSetChanged();
                    }
                };
            }
        };

        listView.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(popupView).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();

        listView.setOnItemClickListener((p, view, pos, id) -> {
            File selected = new File(folder, adapter.getItem(pos));
            parseCsv(selected);
            showSignalPicker();
            dialog.dismiss();
        });

        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(android.text.Editable s) {
            }

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
            for (int i = 1; i < headerParts.length; i++) {
                headers.add(headerParts[i].trim());
                values.add(new ArrayList<>());
            }

            String line;
            Double firstTimestamp = null;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < headerParts.length) continue;
                try {
                    double rawT = Double.parseDouble(parts[0]);

                    if (firstTimestamp == null) {
                        firstTimestamp = rawT;
                    }

                    double normalizedSeconds = (rawT - firstTimestamp) / 1000.0;

                    timestamps.add(normalizedSeconds);

                    for (int i = 1; i < parts.length; i++) {
                        values.get(i - 1).add(Double.parseDouble(parts[i]));
                    }
                } catch (NumberFormatException ignored) {
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
                .setMultiChoiceItems(names, checked, (d, i, isChecked) -> checked[i] = isChecked)
                .setPositiveButton("Plot", (d, w) -> plotSignals(checked))
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
            LineAndPointFormatter fmt = new LineAndPointFormatter(color, null, null, null);

            if (fmt.getLinePaint() == null) fmt.setLinePaint(new Paint());
            fmt.getLinePaint().setColor(color);
            fmt.getLinePaint().setStrokeWidth(5);
            fmt.getLinePaint().setAntiAlias(true);

            if (fmt.getFillPaint() == null) fmt.setFillPaint(new Paint());
            int transparentFill = Color.argb(50, Color.red(color), Color.green(color), Color.blue(color));
            fmt.getFillPaint().setColor(transparentFill);
            fmt.setFillDirection(FillDirection.BOTTOM);

            plot.addSeries(series, fmt);
        }

        plot.getLegendWidget().setVisible(true);

        Paint legendBg = new Paint();
        legendBg.setColor(Color.WHITE);
        legendBg.setStyle(Paint.Style.FILL);
        plot.getLegendWidget().setBackgroundPaint(legendBg);
        plot.getLegendWidget().setHeight(50);

        plot.getLegendWidget().getTextPaint().setColor(Color.DKGRAY);
        plot.getLegendWidget().getTextPaint().setTextSize(25);

        plot.redraw();
    }

    private void setupPlotStyle() {
        Paint transparentPaint = new Paint();
        transparentPaint.setColor(Color.TRANSPARENT);
        plot.setBackgroundPaint(transparentPaint);

        Paint whiteBg = new Paint();
        whiteBg.setColor(Color.WHITE);
        whiteBg.setStyle(Paint.Style.FILL);

        plot.setTitle("Measured Data");
        plot.getTitleWidget().getLabelPaint().setTextSize(35);
        plot.getTitleWidget().getLabelPaint().setColor(Color.BLACK);
        plot.getTitleWidget().setBackgroundPaint(whiteBg);
        plot.getTitleWidget().setWidth(300, SizeLayoutType.ABSOLUTE);
        plot.getTitleWidget().setHeight(60, SizeLayoutType.ABSOLUTE);

        XYGraphWidget graph = plot.getGraphWidget();
        graph.getBackgroundPaint().setColor(Color.WHITE);
        graph.getGridBackgroundPaint().setColor(Color.WHITE);

        plot.setDomainLabel("Time (s)");
        plot.getDomainLabelWidget().getLabelPaint().setTextSize(30);
        plot.getDomainLabelWidget().getLabelPaint().setColor(Color.BLACK);
        plot.getDomainLabelWidget().setBackgroundPaint(whiteBg);
        plot.getDomainLabelWidget().setHeight(50, SizeLayoutType.ABSOLUTE);
        plot.getDomainLabelWidget().setWidth(150, SizeLayoutType.ABSOLUTE);

        plot.setRangeLabel("Value");
        plot.getRangeLabelWidget().getLabelPaint().setTextSize(30);
        plot.getRangeLabelWidget().getLabelPaint().setColor(Color.BLACK);
        plot.getRangeLabelWidget().setBackgroundPaint(whiteBg);
        plot.getRangeLabelWidget().setHeight(150, SizeLayoutType.ABSOLUTE);
        plot.getRangeLabelWidget().setWidth(50, SizeLayoutType.ABSOLUTE);

        graph.getDomainLabelPaint().setTextSize(25);
        graph.getDomainLabelPaint().setColor(Color.DKGRAY);
        graph.getRangeLabelPaint().setTextSize(25);
        graph.getRangeLabelPaint().setColor(Color.DKGRAY);

        graph.setMarginBottom(50);
        graph.setMarginLeft(50);
        graph.setMarginTop(20);
        graph.setMarginRight(30);
        plot.setPlotPadding(0, 0, 0, 20);

        plot.disableAllMarkup();

    }

}