package com.ruben.chemistry.quantum.linalg;

import java.util.Arrays;

/**
 * A dense real matrix stored row by row.
 */
public final class Matrix
{
    private final int rows;
    private final int columns;
    private final double[] data;

    /**
     * Creates a matrix of zeros.
     *
     * @param rows the number of rows
     * @param columns the number of columns
     * @throws IllegalArgumentException if either dimension is negative
     */
    public Matrix(int rows, int columns)
    {
        if (rows < 0 || columns < 0)
        {
            throw new IllegalArgumentException("negative dimension " + rows + " x " + columns);
        }
        this.rows = rows;
        this.columns = columns;
        this.data = new double[rows * columns];
    }

    /**
     * @param size the number of rows and of columns
     * @return the identity matrix
     */
    public static Matrix identity(int size)
    {
        Matrix result = new Matrix(size, size);
        for (int i = 0; i < size; i++)
        {
            result.set(i, i, 1.0);
        }
        return result;
    }

    /**
     * @param values the rows, all of the same length
     * @return a matrix holding a copy of {@code values}
     * @throws IllegalArgumentException if the rows differ in length
     */
    public static Matrix of(double[][] values)
    {
        int columns = values.length == 0 ? 0 : values[0].length;
        Matrix result = new Matrix(values.length, columns);
        for (int i = 0; i < values.length; i++)
        {
            if (values[i].length != columns)
            {
                throw new IllegalArgumentException("row " + i + " has " + values[i].length + " entries, expected " + columns);
            }
            System.arraycopy(values[i], 0, result.data, i * columns, columns);
        }
        return result;
    }

    /**
     * @return the number of rows
     */
    public int rows()
    {
        return rows;
    }

    /**
     * @return the number of columns
     */
    public int columns()
    {
        return columns;
    }

    /**
     * @param row the row index
     * @param column the column index
     * @return the entry
     * @throws IndexOutOfBoundsException if the position is outside the matrix
     */
    public double get(int row, int column)
    {
        return data[index(row, column)];
    }

    /**
     * @param row the row index
     * @param column the column index
     * @param value the new entry
     * @throws IndexOutOfBoundsException if the position is outside the matrix
     */
    public void set(int row, int column, double value)
    {
        data[index(row, column)] = value;
    }

    /**
     * @return an independent copy
     */
    public Matrix copy()
    {
        Matrix result = new Matrix(rows, columns);
        System.arraycopy(data, 0, result.data, 0, data.length);
        return result;
    }

    /**
     * @return the transpose
     */
    public Matrix transpose()
    {
        Matrix result = new Matrix(columns, rows);
        for (int i = 0; i < rows; i++)
        {
            for (int j = 0; j < columns; j++)
            {
                result.data[j * rows + i] = data[i * columns + j];
            }
        }
        return result;
    }

    /**
     * @param other a matrix of the same shape
     * @return the entrywise sum
     * @throws IllegalArgumentException if the shapes differ
     */
    public Matrix plus(Matrix other)
    {
        requireSameShape(other);
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < data.length; i++)
        {
            result.data[i] = data[i] + other.data[i];
        }
        return result;
    }

    /**
     * @param other a matrix of the same shape
     * @return this less {@code other}, entry by entry
     * @throws IllegalArgumentException if the shapes differ
     */
    public Matrix minus(Matrix other)
    {
        requireSameShape(other);
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < data.length; i++)
        {
            result.data[i] = data[i] - other.data[i];
        }
        return result;
    }

    /**
     * @param factor a number
     * @return every entry multiplied by {@code factor}
     */
    public Matrix times(double factor)
    {
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < data.length; i++)
        {
            result.data[i] = data[i] * factor;
        }
        return result;
    }

    /**
     * @param other a matrix with as many rows as this has columns
     * @return the matrix product of this and {@code other}
     * @throws IllegalArgumentException if the inner dimensions differ
     */
    public Matrix times(Matrix other)
    {
        if (columns != other.rows)
        {
            throw new IllegalArgumentException("cannot multiply " + rows + " x " + columns + " by " + other.rows + " x " + other.columns);
        }
        Matrix result = new Matrix(rows, other.columns);
        for (int i = 0; i < rows; i++)
        {
            for (int k = 0; k < columns; k++)
            {
                double value = data[i * columns + k];
                if (value == 0.0)
                {
                    continue;
                }
                for (int j = 0; j < other.columns; j++)
                {
                    result.data[i * other.columns + j] += value * other.data[k * other.columns + j];
                }
            }
        }
        return result;
    }

    /**
     * @return the sum of the diagonal
     * @throws IllegalStateException if the matrix is not square
     */
    public double trace()
    {
        requireSquare();
        double sum = 0.0;
        for (int i = 0; i < rows; i++)
        {
            sum += data[i * columns + i];
        }
        return sum;
    }

    /**
     * @param other a matrix of the same shape
     * @return the sum over every position of the product of the two entries, which is the trace of this times
     *     the transpose of {@code other}
     * @throws IllegalArgumentException if the shapes differ
     */
    public double dot(Matrix other)
    {
        requireSameShape(other);
        double sum = 0.0;
        for (int i = 0; i < data.length; i++)
        {
            sum += data[i] * other.data[i];
        }
        return sum;
    }

    /**
     * @return the square root of the sum of the squared entries
     */
    public double frobeniusNorm()
    {
        return Math.sqrt(dot(this));
    }

    /**
     * @param tolerance the largest difference allowed between an entry and its mirror, relative to the
     *     largest entry
     * @return whether the matrix is square and equal to its transpose within {@code tolerance}
     */
    public boolean isSymmetric(double tolerance)
    {
        if (rows != columns)
        {
            return false;
        }
        double scale = 0.0;
        for (double value : data)
        {
            scale = Math.max(scale, Math.abs(value));
        }
        for (int i = 0; i < rows; i++)
        {
            for (int j = i + 1; j < columns; j++)
            {
                if (Math.abs(data[i * columns + j] - data[j * columns + i]) > tolerance * Math.max(scale, 1.0))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @param column the column index
     * @return a copy of that column
     */
    public double[] column(int column)
    {
        double[] result = new double[rows];
        for (int i = 0; i < rows; i++)
        {
            result[i] = get(i, column);
        }
        return result;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof Matrix matrix && rows == matrix.rows && columns == matrix.columns && Arrays.equals(data, matrix.data);
    }

    @Override
    public int hashCode()
    {
        return 31 * (31 * rows + columns) + Arrays.hashCode(data);
    }

    private int index(int row, int column)
    {
        if (row < 0 || row >= rows || column < 0 || column >= columns)
        {
            throw new IndexOutOfBoundsException("(" + row + ", " + column + ") is outside " + rows + " x " + columns);
        }
        return row * columns + column;
    }

    private void requireSameShape(Matrix other)
    {
        if (rows != other.rows || columns != other.columns)
        {
            throw new IllegalArgumentException("shapes differ: " + rows + " x " + columns + " and " + other.rows + " x " + other.columns);
        }
    }

    private void requireSquare()
    {
        if (rows != columns)
        {
            throw new IllegalStateException("matrix is " + rows + " x " + columns + ", not square");
        }
    }
}
