name: 🌐 Source request
description: Suggest a new source for CloudStream
labels: [source request]
body:

  - type: input
    id: name
    attributes:
      label: Source name
      placeholder: |
        Example: "Example Ecinema"
    validations:
      required: true

  - type: input
    id: link
    attributes:
      label: Source link
      placeholder: |
        Example: "https://source.com"
    validations:
      required: true

  - type: input
    id: language
    attributes:
      label: Language
      placeholder: |
        Example: "English"
    validations:
      required: true

  - type: textarea
    id: other-details
    attributes:
      label: Other details
      placeholder: |
        Additional details and attachments.

  - type: checkboxes
    id: acknowledgements
    attributes:
      label: Acknowledgements
      description: Your issue will be closed if you haven't done these steps.
      options:
        - label: I have searched the existing issues and this is a new ticket, **NOT** a duplicate or related to another open issue.
          required: true
        - label: I have written a title with source name.
          required: true
        - label: I have checked that the source does not already exist on the app.
          required: true
        - label: I have checked that the source does not already exist by searching the [GitHub repository](https://github.com/LagradOst/CloudStream-3) and verified it does not appear in the code base.
          required: true
        - label: I will fill out all of the requested information in this form.
          required: true
