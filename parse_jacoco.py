import xml.etree.ElementTree as ET
import os

def parse_jacoco_report(xml_content):
    """
    Parses the JaCoCo XML report and identifies classes with less than 50% line coverage.

    Args:
        xml_content (str): The content of the jacoco.xml file.

    Returns:
        list: A list of class names with less than 50% line coverage.
    """
    root = ET.fromstring(xml_content)
    classes_for_unit_tests = []

    for package in root.findall('.//package'):
        # package_name = package.get('name') # Not used, can be removed or commented out
        for clazz in package.findall('.//class'):
            class_name_full = clazz.get('name')
            # JaCoCo class names use '/' instead of '.'
            class_name_java = class_name_full.replace('/', '.')
            
            line_counter = None
            for counter in clazz.findall('counter'):
                if counter.get('type') == 'LINE':
                    line_counter = counter
                    break
            
            if line_counter is not None: # Changed to 'is not None' to avoid DeprecationWarning
                missed_lines = int(line_counter.get('missed', '0'))
                covered_lines = int(line_counter.get('covered', '0'))
                
                total_lines = missed_lines + covered_lines
                if total_lines > 0: # Avoid division by zero
                    coverage_percentage = (covered_lines / total_lines) * 100
                    if coverage_percentage < 50:
                        classes_for_unit_tests.append(class_name_java)
                # If a class has no lines of code (total_lines == 0), it technically has 100% coverage
                # or can be ignored. We'll ignore it as it doesn't need tests.
                # The 'else' block for missed_lines > 0 and covered_lines == 0 is covered by coverage_percentage < 50
    return classes_for_unit_tests

def generate_markdown_report(classes, output_file):
    """
    Generates a Markdown report from the list of classes.

    Args:
        classes (list): A list of class names.
        output_file (str): The path to the output Markdown file.
    """
    with open(output_file, 'w') as f:
        f.write("# Classes Requiring Unit Tests (Less than 50% Line Coverage)\n\n")
        if classes:
            f.write("The following classes have less than 50% line coverage and require unit tests:\n\n")
            for clazz in sorted(classes):
                f.write(f"- `{clazz}`\n")
        else:
            f.write("All classes have 50% or more line coverage. Great job!\n")

if __name__ == "__main__":
    jacoco_xml_path = "/Users/chris/Development/greenways/hara.lang/target/site/jacoco/jacoco.xml"
    output_md_path = "/Users/chris/Development/greenways/hara.lang/unit_test_action_items.md"

    try:
        with open(jacoco_xml_path, 'r') as f:
            xml_content = f.read()
    except FileNotFoundError:
        print(f"Error: {jacoco_xml_path} not found. Please ensure JaCoCo report is generated.")
        exit(1)

    classes_to_test = parse_jacoco_report(xml_content)
    generate_markdown_report(classes_to_test, output_md_path)
    print(f"Generated unit test action items report: {output_md_path}")