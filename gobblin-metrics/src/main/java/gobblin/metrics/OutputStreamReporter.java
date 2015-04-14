/*
 * (c) 2014 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.metrics;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class OutputStreamReporter extends ScheduledReporter implements Closeable {

  /**
   * Returns a new {@link Builder} for {@link gobblin.metrics.OutputStreamReporter}.
   *
   * @param registry the registry to report
   * @return a {@link Builder} instance for a {@link gobblin.metrics.OutputStreamReporter}
   */
  public static Builder<?> forRegistry(MetricRegistry registry) {
    return new BuilderImpl(registry);
  }

  private static class BuilderImpl extends Builder<BuilderImpl> {
    public BuilderImpl(MetricRegistry registry) { super(registry); }

    @Override
    protected BuilderImpl self() {
      return this;
    }
  }

  /**
   * A builder for {@link gobblin.metrics.OutputStreamReporter} instances.
   * Defaults to using the default locale and
   * time zone, writing to {@code System.out}, converting rates to events/second, converting
   * durations to milliseconds, and not filtering metrics.
   */
  public static abstract class Builder<T extends Builder<T>> {
    protected final MetricRegistry registry;
    protected PrintStream output;
    protected Locale locale;
    protected Clock clock;
    protected TimeZone timeZone;
    protected TimeUnit rateUnit;
    protected TimeUnit durationUnit;
    protected MetricFilter filter;
    protected Map<String, String> tags;

    protected Builder(MetricRegistry registry) {
      this.registry = registry;
      this.output = System.out;
      this.locale = Locale.getDefault();
      this.clock = Clock.defaultClock();
      this.timeZone = TimeZone.getDefault();
      this.rateUnit = TimeUnit.SECONDS;
      this.durationUnit = TimeUnit.MILLISECONDS;
      this.filter = MetricFilter.ALL;
      this.tags = new HashMap<String, String>();
    }

    protected abstract T self();

    /**
     * Write to the given {@link PrintStream}.
     *
     * @param output a {@link PrintStream} instance.
     * @return {@code this}
     */
    public T outputTo(PrintStream output) {
      this.output = output;
      return self();
    }

    /**
     * Write to the given {@link java.io.OutputStream}
     * @param stream 2 {@link java.io.OutputStream} instance
     * @return {@code this}
     */
    public T outputTo(OutputStream stream) {
      this.output = new PrintStream(stream);
      return self();
    }

    /**
     * Format numbers for the given {@link Locale}.
     *
     * @param locale a {@link Locale}
     * @return {@code this}
     */
    public T formattedFor(Locale locale) {
      this.locale = locale;
      return self();
    }

    /**
     * Use the given {@link Clock} instance for the time.
     *
     * @param clock a {@link Clock} instance
     * @return {@code this}
     */
    public T withClock(Clock clock) {
      this.clock = clock;
      return self();
    }

    /**
     * Use the given {@link TimeZone} for the time.
     *
     * @param timeZone a {@link TimeZone}
     * @return {@code this}
     */
    public T formattedFor(TimeZone timeZone) {
      this.timeZone = timeZone;
      return self();
    }

    /**
     * Convert rates to the given time unit.
     *
     * @param rateUnit a unit of time
     * @return {@code this}
     */
    public T convertRatesTo(TimeUnit rateUnit) {
      this.rateUnit = rateUnit;
      return self();
    }

    /**
     * Convert durations to the given time unit.
     *
     * @param durationUnit a unit of time
     * @return {@code this}
     */
    public T convertDurationsTo(TimeUnit durationUnit) {
      this.durationUnit = durationUnit;
      return self();
    }

    /**
     * Adds tags to the reporter
     *
     * @param tags key-value pairs
     * @return {@code this}
     */
    public T withTags(Map<String, String> tags) {
      this.tags.putAll(tags);
      return  self();
    }

    /**
     * Only report metrics which match the given filter.
     *
     * @param filter a {@link MetricFilter}
     * @return {@code this}
     */
    public T filter(MetricFilter filter) {
      this.filter = filter;
      return self();
    }

    /**
     * Builds a {@link gobblin.metrics.OutputStreamReporter} with the given properties.
     *
     * @return a {@link gobblin.metrics.OutputStreamReporter}
     */
    public OutputStreamReporter build() {
      return new OutputStreamReporter(this);
    }
  }

  private static final int CONSOLE_WIDTH = 80;

  private final PrintStream output;
  private final Locale locale;
  private final Clock clock;
  private final DateFormat dateFormat;

  public final Map<String, String> tags;

  private OutputStreamReporter(Builder<?> builder) {
    super(builder.registry, "console-reporter", builder.filter,
        builder.rateUnit, builder.durationUnit);
    this.output = builder.output;
    this.locale = builder.locale;
    this.clock = builder.clock;
    this.dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT,
        DateFormat.MEDIUM,
        locale);
    this.tags = builder.tags;
    dateFormat.setTimeZone(builder.timeZone);
  }

  @Override
  public void close() {
    super.close();
    output.close();
  }

  @Override
  public void report(SortedMap<String, Gauge> gauges,
      SortedMap<String, Counter> counters,
      SortedMap<String, Histogram> histograms,
      SortedMap<String, Meter> meters,
      SortedMap<String, Timer> timers) {
    final String dateTime = dateFormat.format(new Date(clock.getTime()));
    printWithBanner(dateTime, '=');
    output.println();

    if (!tags.isEmpty()) {
      printWithBanner("-- Tags", '-');
      for (Map.Entry<String, String> entry : tags.entrySet()) {
        output.println(String.format("%s=%s", entry.getKey(), entry.getValue()));
      }
      output.println();
    }

    if (!gauges.isEmpty()) {
      printWithBanner("-- Gauges", '-');
      for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
        output.println(entry.getKey());
        printGauge(entry);
      }
      output.println();
    }

    if (!counters.isEmpty()) {
      printWithBanner("-- Counters", '-');
      for (Map.Entry<String, Counter> entry : counters.entrySet()) {
        output.println(entry.getKey());
        printCounter(entry);
      }
      output.println();
    }

    if (!histograms.isEmpty()) {
      printWithBanner("-- Histograms", '-');
      for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
        output.println(entry.getKey());
        printHistogram(entry.getValue());
      }
      output.println();
    }

    if (!meters.isEmpty()) {
      printWithBanner("-- Meters", '-');
      for (Map.Entry<String, Meter> entry : meters.entrySet()) {
        output.println(entry.getKey());
        printMeter(entry.getValue());
      }
      output.println();
    }

    if (!timers.isEmpty()) {
      printWithBanner("-- Timers", '-');
      for (Map.Entry<String, Timer> entry : timers.entrySet()) {
        output.println(entry.getKey());
        printTimer(entry.getValue());
      }
      output.println();
    }

    output.println();
    output.flush();
  }

  private void printMeter(Meter meter) {
    output.printf(locale, "             count = %d%n", meter.getCount());
    output.printf(locale, "         mean rate = %2.2f events/%s%n", convertRate(meter.getMeanRate()), getRateUnit());
    output.printf(locale, "     1-minute rate = %2.2f events/%s%n", convertRate(meter.getOneMinuteRate()), getRateUnit());
    output.printf(locale, "     5-minute rate = %2.2f events/%s%n", convertRate(meter.getFiveMinuteRate()), getRateUnit());
    output.printf(locale, "    15-minute rate = %2.2f events/%s%n", convertRate(meter.getFifteenMinuteRate()), getRateUnit());
  }

  private void printCounter(Map.Entry<String, Counter> entry) {
    output.printf(locale, "             count = %d%n", entry.getValue().getCount());
  }

  private void printGauge(Map.Entry<String, Gauge> entry) {
    output.printf(locale, "             value = %s%n", entry.getValue().getValue());
  }

  private void printHistogram(Histogram histogram) {
    output.printf(locale, "             count = %d%n", histogram.getCount());
    Snapshot snapshot = histogram.getSnapshot();
    output.printf(locale, "               min = %d%n", snapshot.getMin());
    output.printf(locale, "               max = %d%n", snapshot.getMax());
    output.printf(locale, "              mean = %2.2f%n", snapshot.getMean());
    output.printf(locale, "            stddev = %2.2f%n", snapshot.getStdDev());
    output.printf(locale, "            median = %2.2f%n", snapshot.getMedian());
    output.printf(locale, "              75%% <= %2.2f%n", snapshot.get75thPercentile());
    output.printf(locale, "              95%% <= %2.2f%n", snapshot.get95thPercentile());
    output.printf(locale, "              98%% <= %2.2f%n", snapshot.get98thPercentile());
    output.printf(locale, "              99%% <= %2.2f%n", snapshot.get99thPercentile());
    output.printf(locale, "            99.9%% <= %2.2f%n", snapshot.get999thPercentile());
  }

  private void printTimer(Timer timer) {
    final Snapshot snapshot = timer.getSnapshot();
    output.printf(locale, "             count = %d%n", timer.getCount());
    output.printf(locale, "         mean rate = %2.2f calls/%s%n", convertRate(timer.getMeanRate()), getRateUnit());
    output.printf(locale, "     1-minute rate = %2.2f calls/%s%n", convertRate(timer.getOneMinuteRate()), getRateUnit());
    output.printf(locale, "     5-minute rate = %2.2f calls/%s%n", convertRate(timer.getFiveMinuteRate()), getRateUnit());
    output.printf(locale, "    15-minute rate = %2.2f calls/%s%n", convertRate(timer.getFifteenMinuteRate()), getRateUnit());

    output.printf(locale, "               min = %2.2f %s%n", convertDuration(snapshot.getMin()), getDurationUnit());
    output.printf(locale, "               max = %2.2f %s%n", convertDuration(snapshot.getMax()), getDurationUnit());
    output.printf(locale, "              mean = %2.2f %s%n", convertDuration(snapshot.getMean()), getDurationUnit());
    output.printf(locale, "            stddev = %2.2f %s%n", convertDuration(snapshot.getStdDev()), getDurationUnit());
    output.printf(locale, "            median = %2.2f %s%n", convertDuration(snapshot.getMedian()), getDurationUnit());
    output.printf(locale, "              75%% <= %2.2f %s%n", convertDuration(snapshot.get75thPercentile()), getDurationUnit());
    output.printf(locale, "              95%% <= %2.2f %s%n", convertDuration(snapshot.get95thPercentile()), getDurationUnit());
    output.printf(locale, "              98%% <= %2.2f %s%n", convertDuration(snapshot.get98thPercentile()), getDurationUnit());
    output.printf(locale, "              99%% <= %2.2f %s%n", convertDuration(snapshot.get99thPercentile()), getDurationUnit());
    output.printf(locale, "            99.9%% <= %2.2f %s%n", convertDuration(snapshot.get999thPercentile()), getDurationUnit());
  }

  private void printWithBanner(String s, char c) {
    output.print(s);
    output.print(' ');
    for (int i = 0; i < (CONSOLE_WIDTH - s.length() - 1); i++) {
      output.print(c);
    }
    output.println();
  }

}
