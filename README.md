# Cycling Metrics App

## Project Overview

This is a Clojure web application designed to analyze cycling activity data from `.fit` files, typically exported from platforms like Zwift. It provides estimations for Functional Threshold Power (FTP) and suggests personalized training zones based on your performance data. The application aims to help cyclists understand their performance metrics and guide their training.

## Features

-   **FIT File Parsing**: Processes standard `.fit` files to extract relevant cycling data, specifically power output.
-   **FTP Estimation**: Calculates your Functional Threshold Power (FTP) based on your best 20-minute average power from the uploaded `.fit` file.
-   **Training Zones**: Generates personalized training zones (e.g., Active Recovery, Endurance, Tempo, Threshold, VO2 Max, Anaerobic, Neuromuscular) using the estimated FTP.
-   **Simple Web Interface**: A minimalist web UI for easy file uploads and result display, styled with Pico.css.

## Technologies Used

-   **Clojure**: The primary language for the application logic.
-   **clojure CLI**: For project management and running tasks.
-   **http-kit**: A high-performance web server for Clojure.
-   **Reitit**: A fast data-driven router for HTTP requests.
-   **Hiccup**: For generating HTML directly from Clojure code.
-   **Garmin FIT SDK**: Java library (via Clojure interop) for `.fit` file parsing.
-   **Pico.css**: A lightweight CSS framework for styling the user interface.

## Setup Instructions

### Prerequisites

Before you begin, ensure you have the following installed:

-   **Java Development Kit (JDK)** version 11 or higher.
-   **Git**.
-   **Clojure CLI Tools**:

    If you don't have Clojure CLI installed, you can install it using the following steps (assuming a Linux-like environment):

    ```bash
    mkdir -p ~/.local/bin
    export PATH=$PATH:~/.local/bin
    curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
    chmod +x linux-install.sh
    ./linux-install.sh --prefix ~/.local
    rm linux-install.sh
    ```

    *Note: If you have issues with permissions, you might need to adjust the `--prefix` or install globally using `sudo` if preferred.*

### Getting Started

1.  **Clone the Repository** (or download the source code if not using Git):
    ```bash
    git clone YOUR_GITHUB_REPO_URL # Replace with your repo URL, e.g., https://github.com/your-username/cycling-metrics.git
    cd cycling-metrics
    ```

2.  **Restore Dependencies**:
    The Clojure CLI will automatically download all necessary dependencies when you first run the application or tests.

## Usage Examples

### Running the Application

To start the web server:

1.  Navigate to the project root directory:
    ```bash
    cd cycling-metrics
    ```
2.  Run the application:
    ```bash
    clojure -M:run
    ```
    The application will start on `http://localhost:8080`.

3.  Open your web browser and visit `http://localhost:8080`.
    *   You will see a simple page where you can upload a `.fit` file.
    *   Select your `.fit` file and click "Analyze" to see your estimated FTP and training zones.

### Running Tests

To execute the unit tests:

1.  Navigate to the project root directory:
    ```bash
    cd cycling-metrics
    ```
2.  Run the tests using the `:test` alias:
    ```bash
    clojure -M:test
    ```
    The output will show the test results, including any failures or errors.

## Project Structure

-   `src/cycling_metrics/`: Contains the core application source code.
    -   `core.clj`: Main application entry point, responsible for starting the web server.
    -   `web.clj`: Defines HTTP routes and handlers for the web interface (upload form, analysis display).
    -   `fit.clj`: Handles parsing of `.fit` files using the Garmin FIT SDK.
    -   `analysis.clj`: Implements the logic for calculating FTP and training zones.
-   `test/`: Contains unit tests for the application.
    -   `analysis_test.clj`: Unit tests for the `analysis.clj` module.
-   `resources/public/`: Directory for static assets (currently empty, but can be used for CSS, JS, images).
-   `deps.edn`: Clojure CLI configuration file, managing dependencies and aliases.
-   `README.md`: This file.

## Future Enhancements

-   **Strava API Integration**: Pull activity data directly from Strava.
-   **Advanced Analytics**: Implement more sophisticated cycling metrics (e.g., Normalized Power, TSS, W').
-   **Data Visualization**: Add charts and graphs to visualize performance trends.
-   **Workout Plans**: Generate personalized workout plans based on FTP and training goals.
-   **Robust FIT Parsing**: Improve error handling and support for more `.fit` message types.
-   **Frontend Framework**: Consider a more interactive frontend with Reagent/Re-frame or a similar framework.
