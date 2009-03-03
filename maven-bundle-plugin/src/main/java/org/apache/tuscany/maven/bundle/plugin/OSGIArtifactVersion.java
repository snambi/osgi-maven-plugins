package org.apache.tuscany.maven.bundle.plugin;

/*
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.StringTokenizer;

import org.apache.maven.artifact.versioning.ArtifactVersion;

public class OSGIArtifactVersion implements ArtifactVersion {
	private Integer buildNumber;

	private Integer incrementalVersion;

	private Integer majorVersion;

	private Integer minorVersion;

	private String qualifier;

	private String unparsed;

	public OSGIArtifactVersion(String version) {
		parseVersion(version);
	}

	public int compareTo(Object o) {
		ArtifactVersion otherVersion = (ArtifactVersion) o;

		int result = getMajorVersion() - otherVersion.getMajorVersion();
		if (result == 0) {
			result = getMinorVersion() - otherVersion.getMinorVersion();
		}
		if (result == 0) {
			result = getIncrementalVersion() - otherVersion.getIncrementalVersion();
		}
		if (result == 0) {
			if (this.qualifier != null) {
				String otherQualifier = otherVersion.getQualifier();

				if (otherQualifier != null) {
					if ((this.qualifier.length() > otherQualifier.length())
							&& this.qualifier.startsWith(otherQualifier)) {
						// here, the longer one that otherwise match is
						// considered older
						result = -1;
					}
					else if ((this.qualifier.length() < otherQualifier.length())
							&& otherQualifier.startsWith(this.qualifier)) {
						// here, the longer one that otherwise match is
						// considered older
						result = 1;
					}
					else {
						result = this.qualifier.compareTo(otherQualifier);
					}
				}
				else {
					// otherVersion has no qualifier but we do - that's newer
					result = -1;
				}
			}
			else if (otherVersion.getQualifier() != null) {
				// otherVersion has a qualifier but we don't, we're newer
				result = 1;
			}
			else {
				result = getBuildNumber() - otherVersion.getBuildNumber();
			}
		}
		return result;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}

		if (false == (other instanceof ArtifactVersion)) {
			return false;
		}

		return 0 == compareTo(other);
	}

	public int getBuildNumber() {
		return this.buildNumber != null ? this.buildNumber.intValue() : 0;
	}

	public int getIncrementalVersion() {
		return this.incrementalVersion != null ? this.incrementalVersion.intValue() : 0;
	}

	public int getMajorVersion() {
		return this.majorVersion != null ? this.majorVersion.intValue() : 0;
	}

	public int getMinorVersion() {
		return this.minorVersion != null ? this.minorVersion.intValue() : 0;
	}

	public String getQualifier() {
		return this.qualifier;
	}

	@Override
	public int hashCode() {
		int result = 1229;

		result = 1223 * result + getMajorVersion();
		result = 1223 * result + getMinorVersion();
		result = 1223 * result + getIncrementalVersion();
		result = 1223 * result + getBuildNumber();

		if (null != getQualifier()) {
			result = 1223 * result + getQualifier().hashCode();
		}

		return result;
	}

	public final void parseVersion(String version) {
		this.unparsed = version;

		int index = version.indexOf("-");

		String part1;
		String part2 = null;

		if (index < 0) {
			part1 = version;
		}
		else {
			part1 = version.substring(0, index);
			part2 = version.substring(index + 1);
		}

		if (part2 != null) {
			try {
				if ((part2.length() == 1) || !part2.startsWith("0")) {
					this.buildNumber = Integer.valueOf(part2);
				}
				else {
					this.qualifier = part2;
				}
			}
			catch (NumberFormatException e) {
				this.qualifier = part2;
			}
		}

		if ((part1.indexOf(".") < 0) && !part1.startsWith("0")) {
			try {
				this.majorVersion = Integer.valueOf(part1);
			}
			catch (NumberFormatException e) {
				// qualifier is the whole version, including "-"
				this.qualifier = version;
				this.buildNumber = null;
			}
		}
		else {
			StringTokenizer tok = new StringTokenizer(part1, ".");

			String s;

			if (tok.hasMoreTokens()) {
				s = tok.nextToken();
				try {
					this.majorVersion = Integer.valueOf(s);

					if (tok.hasMoreTokens()) {
						s = tok.nextToken();
						try {
							this.minorVersion = Integer.valueOf(s);
							if (tok.hasMoreTokens()) {

								s = tok.nextToken();
								try {
									this.incrementalVersion = Integer.valueOf(s);

								}
								catch (NumberFormatException e) {
									this.qualifier = s;
								}
							}
						}
						catch (NumberFormatException e) {
							this.qualifier = s;
						}
					}
				}
				catch (NumberFormatException e) {
					this.qualifier = s;
				}
			}

			if (tok.hasMoreTokens()) {
				StringBuffer qualifier = new StringBuffer(this.qualifier != null ? this.qualifier : "");
				qualifier.append(tok.nextToken());
				while (tok.hasMoreTokens()) {
					qualifier.append("_");
					qualifier.append(tok.nextToken());
				}

				this.qualifier = qualifier.toString();
			}

		}
	}

	@Override
	public String toString() {
		return this.unparsed;
	}
}
