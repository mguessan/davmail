// Based on this answer https://stackoverflow.com/a/36945256/784804

/*
 * Copyright (c) 2018 Ian Kirk
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package davmail.util;

import com.sun.jna.Library;
import com.sun.jna.Native;

public final class SystemdNotify {

  interface SystemD extends Library {
    final SystemD INSTANCE = Native.load(SYSTEMD_SO, SystemD.class);

    int sd_notify(int unset_environment, String state);
  }

  private static final String SYSTEMD_SO = "systemd";
  private static final String READY      = "READY=1";
  private static final String RELOADING  = "RELOADING=1";
  private static final String STOPPING   = "STOPPING=1";
  private static final String STATUS     = "STATUS=%s";
  private static final String WATCHDOG   = "WATCHDOG=1";
  private static final String MAINPID    = "MAINPID=%d";
  private static final String ERRNO      = "ERRNO=%d";
  private static final String BUSERROR   = "BUSERROR=%s";

  public static void busError(final String error) {
    notify(String.format(BUSERROR, error));
  }

  public static void errno(final int errno) {
    notify(String.format(ERRNO, errno));
  }

  public static void mainPid(final long pid) {
    notify(String.format(MAINPID, pid));
  }

  public static void notify(final String message) {
    try {
      final int returnCode = SystemD.INSTANCE.sd_notify(0, message);
      if (returnCode < 0)
        throw new RuntimeException(
          String.format("sd_notify returned %d", returnCode));
    } catch (UnsatisfiedLinkError e) {
      // libsystemd could not be loaded, ignore and do nothing
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void ready() {
    notify(READY);
  }

  public static void reloading() {
    notify(RELOADING);
  }

  public static void status(final String text) {
    notify(String.format(STATUS, text));
  }

  public static void stopping() {
    notify(STOPPING);
  }

  public static void watchdog() {
    notify(WATCHDOG);
  }

  private SystemdNotify() {
    throw new RuntimeException("This class should not be instantiated");
  }
}
