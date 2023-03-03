package de.rieger.pascal.mazegame;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;


public class MazeGame extends Application implements Runnable {

    public static void main(String[] args) {
        launch(args);
    }

    int[][] maze;

    final static int backgroundCode = 0;
    final static int wallCode = 1;
    final static int pathCode = 2;
    final static int emptyCode = 3;
    final static int visitedCode = 4;

    Canvas canvas;      // the canvas where the maze is drawn and which fills the whole window
    GraphicsContext graphicsContext;  // graphics context for drawing on the canvas

    Color[] color;          // colors associated with the preceding 5 constants;
    int rows = 31;          // number of rows of cells in maze, including a wall around edges
    int columns = 41;       // number of columns of cells in maze, including a wall around edges
    int blockSize = 12;     // size of each cell
    int sleepTime = 4000;   // wait time after solving one maze before making another, in milliseconds
    int speedSleep = 20;    // short delay between steps in making and solving maze


    public void start(Stage stage) {
        color = new Color[] {
                Color.rgb(200,0,0),
                Color.rgb(200,0,0),
                Color.rgb(128,128,255),
                Color.WHITE,
                Color.rgb(200,200,200)
        };
        maze = new int[rows][columns];
        canvas = new Canvas(columns*blockSize, rows*blockSize);
        graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.setFill(color[backgroundCode]);
        graphicsContext.fillRect(0,0,canvas.getWidth(),canvas.getHeight());
        Pane root = new Pane(canvas);
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setTitle("Maze Generator/Solve");
        stage.show();
        Thread runner = new Thread(this);
        runner.setDaemon(true);  // so thread won't stop program from ending
        runner.start();
    }

    void drawSquare( int row, int column, int colorCode ) {
        // Fill specified square of the grid
        Platform.runLater( () -> {
            graphicsContext.setFill( color[colorCode] );
            int x = blockSize * column;
            int y = blockSize * row;
            graphicsContext.fillRect(x,y,blockSize,blockSize);
        });
    }

    public void run() {
        // Run method for thread repeatedly makes a maze and then solves it.
        while (true) {
            try { Thread.sleep(1000); } // wait a bit before starting
            catch (InterruptedException e) { }
            makeMaze();
            solveMaze(1,1);
            synchronized(this) {
                try { wait(sleepTime); }
                catch (InterruptedException e) { }
            }
            Platform.runLater( () -> {
                graphicsContext.setFill(color[backgroundCode]);
                graphicsContext.fillRect(0,0,canvas.getWidth(),canvas.getHeight());
            });
        }
    }

    void makeMaze() {
        // Create a random maze.
        int i,j;
        int emptyCt = 0; // number of rooms
        int wallCt = 0;  // number of walls
        int[] wallrow = new int[(rows*columns)/2];  // position of walls between rooms
        int[] wallcol = new int[(rows*columns)/2];
        for (i = 0; i<rows; i++)  // start with everything being a wall
            for (j = 0; j < columns; j++)
                maze[i][j] = wallCode;
        for (i = 1; i<rows-1; i += 2)  { // make a grid of empty rooms
            for (j = 1; j<columns-1; j += 2) {
                emptyCt++;
                maze[i][j] = -emptyCt;  // each room is represented by a different negative number
                if (i < rows-2) {  // record info about wall below this room
                    wallrow[wallCt] = i+1;
                    wallcol[wallCt] = j;
                    wallCt++;
                }
                if (j < columns-2) {  // record info about wall to right of this room
                    wallrow[wallCt] = i;
                    wallcol[wallCt] = j+1;
                    wallCt++;
                }
            }
        }
        Platform.runLater( () -> {
            graphicsContext.setFill( color[emptyCode] );
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    if (maze[r][c] < 0)
                        graphicsContext.fillRect( c*blockSize, r*blockSize, blockSize, blockSize );
                }
            }
        });
        synchronized(this) {
            try { wait(1000); }
            catch (InterruptedException e) { }
        }
        int r;
        for (i=wallCt-1; i>0; i--) {
            r = (int)(Math.random() * i);  // choose a wall randomly
            tearDown(wallrow[r],wallcol[r]);
            wallrow[r] = wallrow[i];
            wallcol[r] = wallcol[i];
        }
        for (i=1; i<rows-1; i++)  // replace negative values in maze[][] with emptyCode
            for (j=1; j<columns-1; j++)
                if (maze[i][j] < 0)
                    maze[i][j] = emptyCode;
        synchronized(this) {
            try { wait(1000); }
            catch (InterruptedException e) { }
        }
    }

    void tearDown(int row, int col) {
        // Tear down a wall, unless doing so will form a loop.
        if (row % 2 == 1 && maze[row][col-1] != maze[row][col+1]) {
            // row is odd; wall separates rooms horizontally
            fill(row, col-1, maze[row][col-1], maze[row][col+1]);
            maze[row][col] = maze[row][col+1];
            drawSquare(row,col,emptyCode);
            synchronized(this) {
                try { wait(speedSleep); }
                catch (InterruptedException e) { }
            }
        }
        else if (row % 2 == 0 && maze[row-1][col] != maze[row+1][col]) {
            // row is even; wall separates rooms vertically
            fill(row-1, col, maze[row-1][col], maze[row+1][col]);
            maze[row][col] = maze[row+1][col];
            drawSquare(row,col,emptyCode);
            synchronized(this) {
                try { wait(speedSleep); }
                catch (InterruptedException e) { }
            }
        }
    }

    void fill(int row, int col, int replace, int replaceWith) {
        // called by tearDown() to change "room codes".
        if (maze[row][col] == replace) {
            maze[row][col] = replaceWith;
            fill(row+1,col,replace,replaceWith);
            fill(row-1,col,replace,replaceWith);
            fill(row,col+1,replace,replaceWith);
            fill(row,col-1,replace,replaceWith);
        }
    }

    boolean solveMaze(int row, int col) {
        // Try to solve the maze by continuing current path from position
        // (row,col).  Return true if a solution is found.  The maze is
        // considered to be solved if the path reaches the lower right cell.
        if (maze[row][col] == emptyCode) {
            maze[row][col] = pathCode;      // add this cell to the path
            drawSquare(row,col,pathCode);
            if (row == rows-2 && col == columns-2)
                return true;  // path has reached goal
            try { Thread.sleep(speedSleep); }
            catch (InterruptedException e) { }
            if ( solveMaze(row-1,col)  ||     // try to solve maze by extending path
                    solveMaze(row,col-1)  ||     //    in each possible direction
                    solveMaze(row+1,col)  ||
                    solveMaze(row,col+1) )
                return true;
            maze[row][col] = visitedCode;   // mark cell as having been visited
            drawSquare(row,col,visitedCode);
            synchronized(this) {
                try { wait(speedSleep); }
                catch (InterruptedException e) { }
            }
        }
        return false;
    }

}