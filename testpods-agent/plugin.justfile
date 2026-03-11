# TestPods Agent — plugin script commands
#
# Plugin runtime scripts (placeholder).
# Imported by the root justfile; can also run standalone: just -f plugin.justfile <recipe>

set working-directory := "plugin"

# Show plugin status
status:
    @echo "testpods-agent plugin is installed"
