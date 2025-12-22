# Cycling Metrics App

## Project Overview

This is a Clojure web application designed to analyze cycling activity data from `.fit` files, typically exported from platforms like Zwift or bike computers. It goes beyond simple data extraction to provide actionable insights, including **Functional Threshold Power (FTP) estimation**, **Power-to-Weight Ratio (W/kg)** analysis, and personalized **Power and Heart Rate Training Zones**.

The application features a minimalist, responsive web interface styled with Pico.css and utilizes Chart.js for interactive data visualization.

## Features

-   **FIT File Parsing**: Extracts both **Power** (Watts) and **Heart Rate** (bpm) data from standard `.fit` files.
-   **FTP Estimation**: Automatically detects the best 20-minute power effort to estimate FTP.
    -   *Validation*: Includes an "Estimated LTHR" check to warn if the effort might not have been maximal.
-   **Performance Metrics**:
    -   **W/kg Calculation**: Calculates Watts per Kilogram based on user weight.
    -   **Performance Classification**: Classifies rider level (e.g., "Moderate", "Elite") based on gender-specific power profiling standards.
-   **Training Zones**:
    -   **Power Zones**: 7-zone Coggan model (Active Recovery to Neuromuscular).
    -   **Heart Rate Zones**: 5-zone Friel/Coggan model based on Max HR.
-   **Time-in-Zone Analysis**: Visualizes how much time was spent in each power zone during the ride using interactive charts.
-   **Inclusive Gender Options**: Supports classification for Transgender (MTF/FTM) and Non-Binary athletes, mapping to physiological standards where appropriate.

## The Science Behind the Calculations

This application uses established physiological models to derive its metrics.

### 1. Functional Threshold Power (FTP)
**The Model**: 95% of 20-Minute Power  
**Reference**: Hunter Allen and Andrew Coggan, *Training and Racing with a Power Meter*.

The "standard" field test for FTP is a 20-minute all-out time trial. Since FTP represents the power you can sustain for approximately one hour, taking 95% of the 20-minute average corrects for the anaerobic energy contribution present in the shorter duration effort.

### 2. Power Training Zones
**The Model**: Coggan Power Zones (7 Zones)  
**Reference**: *Training and Racing with a Power Meter*.

Zones are calculated as percentages of FTP:
-   **Zone 1 (Active Recovery)**: < 55% FTP
-   **Zone 2 (Endurance)**: 56% - 75% FTP
-   **Zone 3 (Tempo)**: 76% - 90% FTP
-   **Zone 4 (Lactate Threshold)**: 91% - 105% FTP
-   **Zone 5 (VO2 Max)**: 106% - 120% FTP
-   **Zone 6 (Anaerobic Capacity)**: 121% - 150% FTP
-   **Zone 7 (Neuromuscular Power)**: > 150% FTP

### 3. Heart Rate Zones
**The Model**: Percentage of Max Heart Rate (Simplified)  
**Reference**: Joe Friel, *The Cyclist's Training Bible*.

When Max HR is provided, zones are estimated as:
-   **Zone 1 (Recovery)**: < 60% Max HR
-   **Zone 2 (Aerobic)**: 60% - 69% Max HR
-   **Zone 3 (Tempo)**: 70% - 79% Max HR
-   **Zone 4 (SubThreshold)**: 80% - 89% Max HR
-   **Zone 5 (SuperThreshold)**: > 90% Max HR

### 4. Power Profiling (W/kg) & Classification
**The Model**: Power Profile Chart (20-minute column)  
**Reference**: Coggan/Allen Power Profile Tables.

The application classifies performance by comparing your W/kg (FTP / Weight) against standard benchmarks.

**Gender Classification Standards:**
The application maps user-selected gender identities to the most appropriate physiological baseline for *classification purposes only*. This is based on current competitive cycling guidelines (e.g., UCI) which acknowledge the physiological differences retained or developed during puberty.

*   **Female / Transgender (MTF) / Non-Binary (Female Standards)**: Mapped to *Female* power profile standards.
    *   *Note*: While hormone therapy affects performance, current scientific consensus and sporting regulations (e.g., UCI as of 2023) often group Transgender women who transitioned post-puberty in "Open" or specific categories. However, for the purpose of a *personal training tool*, comparing against the Female standard is often the user's intent for tracking relative progress within their identity, though it's acknowledged that physiological baselines vary.
*   **Male / Transgender (FTM) / Non-Binary (Male Standards)**: Mapped to *Male* power profile standards.
    *   *Note*: Transgender men taking testosterone typically develop muscle mass and haemoglobin levels comparable to cisgender men, making the Male standard the appropriate physiological baseline for training metrics.

**References:**
-   *Union Cycliste Internationale (UCI)* Policy on Transgender Athletes (2023).
-   *Harper, J. et al.* (2021). "Haemoglobin levels in transgender people and their implications for elite sport". *British Journal of Sports Medicine*.
-   *Hilton, E.N. & Lundberg, T.R.* (2021). "Transgender Women in the Female Category of Sport: Perspectives on Testosterone Suppression and Performance Advantage". *Sports Medicine*.

**Important**: These classifications are for *personal training metrics* to help set zones and track progress. They do not constitute an official eligibility check for competition.

## Setup Instructions

### Prerequisites

-   **Java Development Kit (JDK)** version 11 or higher.
-   **Git**.
-   **Clojure CLI Tools** (v1.10+).

### Getting Started

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/your-username/cycling-metrics.git
    cd cycling-metrics
    ```

2.  **Run the Application**:
    ```bash
    clojure -M:run
    ```
    The server will start at `http://localhost:8080`.

3.  **Run Tests**:
    ```bash
    clojure -M:test
    ```

## Usage

1.  Open `http://localhost:8080`.
2.  **Upload**: Select a `.fit` file (from Zwift, Garmin, Wahoo, etc.).
3.  **Profile (Optional but Recommended)**:
    -   Enter **Weight** for W/kg calculation.
    -   Select **Gender / Category** for accurate performance classification.
    -   Enter **Max Heart Rate** to generate HR zones and validate effort intensity.
4.  **Analyze**: View your charts, FTP estimate, and training zones.

## Project Structure

-   `src/cycling_metrics/`:
    -   `core.clj`: App entry point & server start.
    -   `web.clj`: Reitit routes, Hiccup HTML generation, and Chart.js integration.
    -   `fit.clj`: Interop with Garmin FIT SDK to parse files.
    -   `analysis.clj`: Mathematical models for FTP, Zones, and Statistics.
-   `test/`: Unit tests for analysis logic.
-   `deps.edn`: Project dependencies.

## Technologies Used

-   **Clojure**: Backend logic.
-   **http-kit**: Web server.
-   **Reitit**: Routing.
-   **Hiccup**: HTML templating.
-   **Garmin FIT SDK**: File parsing.
-   **Chart.js**: Frontend data visualization.
-   **Pico.css**: UI styling.
