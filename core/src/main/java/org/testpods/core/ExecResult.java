package org.testpods.core;

import java.util.Objects;
import org.testpods.core.pods.TestPod;

/**
 * Result of executing a command inside a container.
 *
 * <p>This is returned by {@link TestPod#exec(String...)} and contains the exit code, stdout, and
 * stderr of the executed command.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * ExecResult result = mongoPod.exec("mongosh", "--eval", "db.stats()");
 * if (result.isSuccess()) {
 *     System.out.println(result.stdout());
 * } else {
 *     System.err.println("Command failed: " + result.stderr());
 * }
 * }</pre>
 *
 * @param exitCode The exit code of the command (0 typically indicates success)
 * @param stdout The standard output from the command
 * @param stderr The standard error output from the command
 */
public record ExecResult(int exitCode, String stdout, String stderr) {

  /** Canonical constructor with validation. */
  public ExecResult {
    stdout = Objects.requireNonNullElse(stdout, "");
    stderr = Objects.requireNonNullElse(stderr, "");
  }

  /**
   * Create a successful result with only stdout.
   *
   * @param stdout The standard output
   * @return An ExecResult with exit code 0
   */
  public static ExecResult success(String stdout) {
    return new ExecResult(0, stdout, "");
  }

  /**
   * Create a failed result with exit code and stderr.
   *
   * @param exitCode The non-zero exit code
   * @param stderr The standard error output
   * @return An ExecResult with the specified exit code
   */
  public static ExecResult failure(int exitCode, String stderr) {
    return new ExecResult(exitCode, "", stderr);
  }

  /**
   * Check if the command succeeded (exit code 0).
   *
   * @return true if exitCode is 0, false otherwise
   */
  public boolean isSuccess() {
    return exitCode == 0;
  }

  /**
   * Check if the command failed (non-zero exit code).
   *
   * @return true if exitCode is non-zero, false otherwise
   */
  public boolean isFailure() {
    return exitCode != 0;
  }

  /**
   * Get combined stdout and stderr output.
   *
   * <p>Useful when you just want all output regardless of stream.
   *
   * @return Combined output with stderr appended after stdout
   */
  public String getOutput() {
    if (stdout.isEmpty()) {
      return stderr;
    }
    if (stderr.isEmpty()) {
      return stdout;
    }
    return stdout + "\n" + stderr;
  }

  /**
   * Get stdout trimmed of leading/trailing whitespace.
   *
   * @return Trimmed stdout
   */
  public String getStdoutTrimmed() {
    return stdout.trim();
  }

  /**
   * Get stderr trimmed of leading/trailing whitespace.
   *
   * @return Trimmed stderr
   */
  public String getStderrTrimmed() {
    return stderr.trim();
  }

  /**
   * Throw an exception if the command failed.
   *
   * @return this ExecResult if successful
   * @throws ExecFailedException if the command exited with non-zero code
   */
  public ExecResult orThrow() {
    if (isFailure()) {
      throw new ExecFailedException(this);
    }
    return this;
  }

  /**
   * Throw an exception with a custom message if the command failed.
   *
   * @param message The error message prefix
   * @return this ExecResult if successful
   * @throws ExecFailedException if the command exited with non-zero code
   */
  public ExecResult orThrow(String message) {
    if (isFailure()) {
      throw new ExecFailedException(message, this);
    }
    return this;
  }

  @Override
  public String toString() {
    return String.format(
        "ExecResult[exitCode=%d, stdout=%d chars, stderr=%d chars]",
        exitCode, stdout.length(), stderr.length());
  }

  /** Exception thrown when a command execution fails. */
  public static class ExecFailedException extends RuntimeException {

    private final ExecResult result;

    public ExecFailedException(ExecResult result) {
      super(
          String.format(
              "Command failed with exit code %d: %s",
              result.exitCode(), result.stderr().isEmpty() ? result.stdout() : result.stderr()));
      this.result = result;
    }

    public ExecFailedException(String message, ExecResult result) {
      super(
          String.format(
              "%s (exit code %d): %s",
              message,
              result.exitCode(),
              result.stderr().isEmpty() ? result.stdout() : result.stderr()));
      this.result = result;
    }

    /**
     * Get the ExecResult that caused this exception.
     *
     * @return The failed ExecResult
     */
    public ExecResult getResult() {
      return result;
    }
  }
}
