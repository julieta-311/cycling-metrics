# Cycling Metrics App

A Clojure web application for analyzing cycling activity data from `.fit` files. It provides **FTP estimation**, **Power-to-Weight Ratio (W/kg)** analysis, and personalized **Training Zones**.

![Example Analysis Chart](resources/public/example-chart.png)

## Features

-   **FTP Estimation**: Estimates Functional Threshold Power (95% of 20-min max avg).
-   **Training Zones**: Calculates 7 Power Zones (Coggan) and 5 Heart Rate Zones (Friel).
-   **Performance Metrics**: W/kg calculation and rider classification (Untrained to Elite).
-   **Dual-Axis Visualization**: Interactive chart correlating Time-in-Zone with Average Heart Rate.
-   **Inclusive Analysis**: Gender-inclusive classification standards (MTF/FTM/Non-Binary).
-   **Data Parsing**: Extracts Power and Heart Rate from standard `.fit` files (Zwift, Garmin, etc.).

## Science & Models

*   **FTP**: 95% of 20-min Power (Allen & Coggan).
*   **Power Zones**: Coggan 7-Zone Model.
*   **Heart Rate**: Friel Percentage of Max HR.
*   **Classification**: Coggan Power Profile Tables (Gender-specific baselines).
    *   *Note*: Transgender and Non-binary identities are mapped to either Male or Female physiological baselines for training metric purposes, based on current competitive guidelines (e.g., UCI 2023) and physiological data (Harper et al. 2021).

## Quick Start

1.  **Clone & Run**:
    ```bash
    git clone https://github.com/your-username/cycling-metrics.git
    cd cycling-metrics
    clojure -M:run
    ```
2.  **Use**: Open `http://localhost:8080`, upload a `.fit` file, and view insights.

## Technologies

Built with **Clojure**, **http-kit**, **Reitit**, **Hiccup**, **Garmin FIT SDK**, **Chart.js**, and **Pico.css**.