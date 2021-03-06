/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.kernel.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.ibm.ws.kernel.boot.internal.BootstrapConstants;

/**
 * Various operating system utilities. This class might be used in things like
 * FAT suites, so avoid using Tr.
 */
public class OperatingSystem {
    /**
     * Get the singleton for the API. By default, this caches the first total RAM
     * calculation.
     *
     * @return Singleton to access memory information.
     */
    public static OperatingSystem instance() {
        return instance;
    }

    /**
     * Get the operating system type.
     *
     * @return Operating system type.
     */
    public OperatingSystemType getOperatingSystemType() {
        return osType;
    }

    private final static OperatingSystem instance = new OperatingSystem();

    private final OperatingSystemType osType = parseOperatingSystemType();

    private static OperatingSystemType parseOperatingSystemType() {
        String osName = System.getProperty("os.name");

        if (osName != null) {
            osName = osName.toLowerCase().replaceAll("/", "");
            if (osName.contains("linux")) {
                return OperatingSystemType.Linux;
            } else if (osName.contains("mac") || osName.contains("darwin")) {
                return OperatingSystemType.Mac;
            } else if (osName.contains("aix")) {
                return OperatingSystemType.AIX;
            } else if (osName.contains("win")) {
                return OperatingSystemType.Windows;
            } else if (osName.contains("400")) {
                return OperatingSystemType.IBMi;
            } else if (osName.contains("os390") || osName.contains("zos")) {
                return OperatingSystemType.zOS;
            } else if (osName.contains("hp")) {
                return OperatingSystemType.HPUX;
            } else if (osName.contains("solaris") || osName.contains("sun")) {
                return OperatingSystemType.Solaris;
            }
        }
        return OperatingSystemType.Unknown;
    }

    private static int cachedPageSize = -1;

    /**
     * Get the base OS page size (this current Java process may not
     * necessarily be using this page size). Not implemented on Windows.
     *
     * @return Page size.
     * @throws OperatingSystemException Error computing page size.
     */
    public static synchronized int getPageSize() throws OperatingSystemException {
        if (cachedPageSize == -1) {
            switch (instance().getOperatingSystemType()) {
                case AIX:
                case HPUX:
                case IBMi:
                case Linux:
                case Mac:
                case Solaris:
                case zOS:
                    cachedPageSize = getPageSizePOSIX();
                    break;
                default:
                    throw new OperatingSystemException(BootstrapConstants.messages.getString("os.pagesize.unavailable"));
            }
        }
        return cachedPageSize;
    }

    private static int getPageSizePOSIX() throws OperatingSystemException {
        List<String> getconfLines = executeProgram("/usr/bin/getconf", "PAGE_SIZE");
        return Integer.parseInt(getconfLines.get(0).trim());
    }

    /**
     * On recent JDKs, this should do a fork that doesn't duplicate the whole
     * virtual address space.
     *
     * @param commandLine
     *            The program and any arguments.
     * @return A list of Strings for the output
     * @throws OperatingSystemException
     *             If return code is not 0.
     */
    public static List<String> executeProgram(String... commandLine) throws OperatingSystemException {
        return executeProgramWithInput(null, commandLine);
    }

    /**
     * On recent JDKs, this should do a fork that doesn't duplicate the whole
     * virtual address space.
     *
     * @param input
     *            Optional stdin.
     * @param commandLine
     *            The program and any arguments.
     * @return A list of Strings for the output
     * @throws OperatingSystemException
     *             If return code is not 0.
     */
    public static List<String> executeProgramWithInput(String input, String... commandLine) throws OperatingSystemException {
        try {
            ProcessBuilder builder = new ProcessBuilder();

            builder.command(commandLine);

            Process process = builder.start();

            if (input != null) {
                try (OutputStream os = process.getOutputStream()) {
                    try (OutputStreamWriter osw = new OutputStreamWriter(os)) {
                        try (BufferedWriter bw = new BufferedWriter(osw)) {
                            bw.write(input);
                            bw.flush();
                        }
                    }
                }
            }

            List<String> lines = new ArrayList<String>();
            try (InputStream is = process.getInputStream()) {
                try (InputStreamReader isr = new InputStreamReader(is)) {
                    try (BufferedReader br = new BufferedReader(isr)) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            lines.add(line);
                        }
                    }
                }
            }

            try {
                int returnCode = process.waitFor();
                if (returnCode != 0) {
                    throw new OperatingSystemException(MessageFormat.format(BootstrapConstants.messages.getString("os.execute.error"), commandLine, returnCode, lines));
                }
                return lines;
            } catch (InterruptedException e) {
                throw new OperatingSystemException(e);
            }
        } catch (IOException e1) {
            throw new OperatingSystemException(e1);
        }
    }
}
