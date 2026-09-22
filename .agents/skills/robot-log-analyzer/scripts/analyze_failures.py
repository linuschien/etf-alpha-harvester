#!/usr/bin/env python3
import xml.etree.ElementTree as ET
import os
import re
import argparse
from datetime import datetime

def parse_args():
    parser = argparse.ArgumentParser(description="Robot Framework XML Output Analyzer & Report Generator")
    parser.add_argument("--xml", "-x", default="engineers/04-tests/reports/output.xml",
                        help="Path to Robot output.xml (default: engineers/04-tests/reports/output.xml)")
    parser.add_argument("--output", "-o", default="engineers/04-tests/reports/e2e-report-{timestamp}.md",
                        help="Path to save markdown report (default: engineers/04-tests/reports/e2e-report-{timestamp}.md)")
    parser.add_argument("--features-dir", "-f", default="docs/02-design-specs/behavior-specs/user/",
                        help="Path to user behavior specs (default: docs/02-design-specs/behavior-specs/user/)")
    parser.add_argument("--requirements-dir", "-r", default="docs/01-requirements/user-stories/",
                        help="Path to requirements user stories (default: docs/01-requirements/user-stories/)")
    return parser.parse_args()

def clean_path(path):
    return os.path.abspath(path)

def file_url(path):
    return f"file://{os.path.abspath(path)}"

def extract_robot_metadata(robot_path):
    metadata = {
        "source_feature": "Unknown",
        "upstream_req": "Unknown",
        "source_feature_link": "",
        "upstream_req_links": []
    }
    
    if not robot_path or not os.path.exists(robot_path):
        return metadata

    try:
        with open(robot_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Look for Settings Documentation block
        doc_match = re.search(r"Documentation\s+(.*?)(?=\n\n|\*\*\*|\s+\w+\s+=)", content, re.DOTALL)
        if doc_match:
            doc_text = doc_match.group(1)
            for line in doc_text.splitlines():
                line = line.strip()
                if "Source Feature" in line:
                    val = line.split(":", 1)[1].strip()
                    metadata["source_feature"] = os.path.basename(val)
                    # Create absolute file link for feature
                    feat_path = clean_path(val)
                    metadata["source_feature_link"] = file_url(feat_path)
                elif "Upstream Req" in line:
                    val = line.split(":", 1)[1].strip()
                    metadata["upstream_req"] = val
                    
                    # Split comma-separated requirements
                    req_list = [r.strip() for r in val.split(",")]
                    req_links = []
                    for req in req_list:
                        req_path = clean_path(os.path.join("docs/01-requirements/user-stories/", req))
                        if not os.path.exists(req_path):
                            # Try general requirements path
                            req_path = clean_path(os.path.join("docs/01-requirements/", req))
                        req_links.append(f"[{req}]({file_url(req_path)})")
                    metadata["upstream_req_links"] = req_links
    except Exception as e:
        print(f"Warning: Failed to extract metadata from {robot_path}: {e}")
        
    return metadata

def find_failed_step_and_message(test_elem):
    # Find the deepest failed keyword/step
    failed_kws = []
    for kw in test_elem.findall(".//kw"):
        status_elem = kw.find("status")
        if status_elem is not None and status_elem.get("status") == "FAIL":
            failed_kws.append(kw)
            
    if not failed_kws:
        return "Unknown Step", "No failure details found in XML."
        
    # The first failed keyword is usually the Gherkin step (Given/When/Then)
    primary_failed_kw = failed_kws[0]
    primary_step = primary_failed_kw.get("name", "Unknown Step")
    
    # Get the failure message from the deepest keyword
    deepest_kw = failed_kws[-1]
    msg_elem = deepest_kw.find(".//msg[@level='FAIL']")
    error_msg = msg_elem.text.strip() if msg_elem is not None and msg_elem.text else ""
    
    if not error_msg:
        status_elem = deepest_kw.find("status")
        if status_elem is not None:
            error_msg = status_elem.text.strip() if status_elem.text else status_elem.get("message", "")
            
    return primary_step, error_msg or "Unknown error message."

def infer_root_cause(error_msg, suite_name):
    error_msg_lower = error_msg.lower()
    
    if "parent suite setup failed" in error_msg_lower:
        return f"A setup step in the '{suite_name}' suite failed. This blocked execution of all subsequent test cases."
    if "500" in error_msg and ("!=" in error_msg or "server error" in error_msg_lower):
        return "The backend returned an HTTP 500 Internal Server Error, suggesting an unhandled server-side crash or database exception."
    if "400" in error_msg and ("200 != 400" in error_msg or "201 != 400" in error_msg):
        return "The backend successfully accepted and persisted a payload containing invalid or out-of-range data that should have failed validation with HTTP 400 Bad Request."
    if "404" in error_msg and "200 != 404" in error_msg:
        return "The backend returned HTTP 200 OK instead of the expected HTTP 404 Not Found when querying a non-existent resource."
    if "none" in error_msg_lower and "floating point" in error_msg_lower:
        return "The backend returned null/None for a grade score mapping, causing a Python type conversion error when the test verified numerical scores."
    if "dictionary does not contain key" in error_msg_lower:
        return "The API response payload structure did not contain the expected keys, which could indicate a resolver failure or incorrect response formatting."
    if "does not contain any of" in error_msg_lower:
        return "The API response structure did not match the expected assertion headers or keys. Check the payload format."
        
    return "Test assertion failed. The system behavior did not match the expected specification. Review the test log for details."

def main():
    args = parse_args()
    
    if not os.path.exists(args.xml):
        print(f"Error: XML file not found at {args.xml}")
        return
        
    print(f"Parsing Robot XML: {args.xml}")
    tree = ET.parse(args.xml)
    root = tree.getroot()
    
    # 1. Total Metrics
    total_tests = 0
    passed_tests = 0
    failed_tests = 0
    
    tests = root.findall(".//test")
    total_tests = len(tests)
    for test in tests:
        status_elem = test.find("status")
        if status_elem is not None:
            status = status_elem.get("status")
            if status == "PASS":
                passed_tests += 1
            elif status == "FAIL":
                failed_tests += 1
                
    pass_rate = (passed_tests / total_tests * 100) if total_tests > 0 else 0.0
    
    # Execution times
    elapsed_time = "Unknown"
    start_time_str = "Unknown"
    
    main_suite = root.find("suite")
    if main_suite is not None:
        status_elem = main_suite.find("status")
        if status_elem is not None:
            elapsed = status_elem.get("elapsed")
            if elapsed:
                elapsed_time = f"{float(elapsed):.2f} seconds"
            start_time = status_elem.get("start")
            if start_time:
                start_time_str = start_time
                
    # 2. Parse Suite hierarchy
    parent_map = {c: p for p in root.iter() for c in p}
    
    # Group tests by suite elements
    suites_data = {}
    
    for test in tests:
        # Get path of parent suites
        path = []
        curr = test
        robot_source = None
        while curr in parent_map:
            curr = parent_map[curr]
            if curr.tag == "suite":
                path.insert(0, curr.get("name"))
                if not robot_source and curr.get("source"):
                    robot_source = curr.get("source")
                    
        suite_name = " -> ".join(path)
        
        if suite_name not in suites_data:
            suites_data[suite_name] = {
                "robot_source": robot_source,
                "total": 0,
                "passed": 0,
                "failed": 0,
                "tests": []
            }
            
        status_elem = test.find("status")
        status = status_elem.get("status") if status_elem is not None else "FAIL"
        
        suites_data[suite_name]["total"] += 1
        if status == "PASS":
            suites_data[suite_name]["passed"] += 1
        else:
            suites_data[suite_name]["failed"] += 1
            
        suites_data[suite_name]["tests"].append((test, status))
        
    # Generate Traceability Matrix and Defect log data
    traceability_rows = []
    defect_sections = []
    
    defect_counter = 1
    
    # Sort suites for consistency
    for suite_name in sorted(suites_data.keys()):
        data = suites_data[suite_name]
        
        # Get metadata from robot file
        meta = extract_robot_metadata(data["robot_source"])
        
        # Link mapping
        req_link_str = ", ".join(meta["upstream_req_links"]) if meta["upstream_req_links"] else meta["upstream_req"]
        spec_link_str = f"[{meta['source_feature']}]({meta['source_feature_link']})" if meta["source_feature_link"] else meta["source_feature"]
        
        # Result status
        if data["failed"] == 0:
            result_str = "**PASS**"
        else:
            result_str = f"**FAIL ({data['failed']}/{data['total']})**"
            
        traceability_rows.append(
            f"| **{suite_name.replace('Cases -> ', '')}** | {req_link_str} | {spec_link_str} | {result_str} |"
        )
        
        # Defect logging
        if data["failed"] > 0:
            suite_defects = []
            suite_defects.append(f"### {suite_name.replace('Cases -> ', '')}\n")
            
            for test_elem, status in data["tests"]:
                if status == "FAIL":
                    test_name = test_elem.get("name")
                    failed_step, error_msg = find_failed_step_and_message(test_elem)
                    root_cause = infer_root_cause(error_msg, suite_name.replace('Cases -> ', ''))
                    
                    suite_defects.append(
                        f"#### Defect-{defect_counter:02d}: {test_name}\n"
                        f"* **Failed Scenario**: `{test_name}`\n"
                        f"* **Upstream Requirement / Spec**: {meta['upstream_req']} / {meta['source_feature']}\n"
                        f"* **Failure Step**: `{failed_step}`\n"
                        f"* **Error Message**: `{error_msg}`\n"
                        f"* **Root Cause Analysis**: {root_cause}\n"
                        f"* **Test Artifact Reference**: [output.xml]({file_url(args.xml)})\n"
                    )
                    defect_counter += 1
            
            defect_sections.append("\n".join(suite_defects))

    # Formulate report markdown
    report_md = []
    report_md.append("# E2E Test Audit Report\n")
    report_md.append(f"**Date of Execution**: {datetime.now().strftime('%Y-%m-%d')}")
    report_md.append(f"**Time of Execution**: {datetime.now().strftime('%H:%M:%S UTC%z')}")
    report_md.append(f"**Execution Environment**: Local Development")
    report_md.append(f"**Test Automation Tool**: Robot Framework\n")
    report_md.append("---\n")
    
    report_md.append("## 📊 Metrics Summary\n")
    report_md.append("| Metric | Value |")
    report_md.append("|---|---|")
    report_md.append(f"| **Total Test Suites** | {len(suites_data)} |")
    report_md.append(f"| **Total Test Cases Executed** | {total_tests} |")
    report_md.append(f"| **Passed Test Cases** | {passed_tests} |")
    report_md.append(f"| **Failed Test Cases** | {failed_tests} |")
    report_md.append(f"| **Pass Rate** | {pass_rate:.2f}% |")
    report_md.append(f"| **Total Elapsed Time** | {elapsed_time} |")
    report_md.append("")
    
    report_md.append("### Suite Breakdown")
    for suite_name in sorted(suites_data.keys()):
        data = suites_data[suite_name]
        p_rate = (data["passed"] / data["total"] * 100) if data["total"] > 0 else 0.0
        short_name = suite_name.replace('Cases -> ', '')
        report_md.append(f"* **{short_name}**: {data['passed']} / {data['total']} Passed ({p_rate:.2f}%)")
    report_md.append("\n---\n")
    
    report_md.append("## 🔗 Traceability Matrix\n")
    report_md.append("| Test Suite | Upstream Requirement ID | Source Behavior Spec | Result |")
    report_md.append("|---|---|---|---|")
    report_md.extend(traceability_rows)
    report_md.append("\n---\n")
    
    report_md.append("## 🐞 Defect Logs\n")
    report_md.append("Under the execution guidelines, **no source code changes were made to address these errors**. The following defects have been identified and logged for development remediation:\n")
    report_md.extend(defect_sections)
    report_md.append("\n---\n")
    
    report_md.append("## 📁 Audit Artifact Location")
    report_md.append(f"* **Robot XML Output**: [output.xml]({file_url(args.xml)})")
    report_md.append(f"* **Robot Log HTML**: [log.html]({file_url(os.path.join(os.path.dirname(args.xml), 'log.html'))})")
    report_md.append(f"* **Robot Report HTML**: [report.html]({file_url(os.path.join(os.path.dirname(args.xml), 'report.html'))})")
    report_md.append("")

    report_content = "\n".join(report_md)
    
    # 3. Write Output
    out_path = args.output
    if "{timestamp}" in out_path:
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        out_path = out_path.replace("{timestamp}", ts)
        
    out_dir = os.path.dirname(out_path)
    if out_dir and not os.path.exists(out_dir):
        os.makedirs(out_dir, exist_ok=True)
        
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(report_content)
        
    print(f"E2E test audit report successfully generated at:\n{os.path.abspath(out_path)}")

if __name__ == "__main__":
    main()
