## FHIR CodeSystem Validate-Code

#### Code System validate-code
http://localhost:8080/fhir/CodeSystem/$validate-code?coding=http://snomed.info/sct|404684003

#### Batch validate-code (multiple codes in one request)

POST a FHIR batch Bundle to the server base URL. Each entry must target `CodeSystem/$validate-code`.
Only `batch` Bundles are supported (not `transaction`).

Example using GET entries:

```http
POST /fhir/
Content-Type: application/fhir+json

{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "request": {
        "method": "GET",
        "url": "CodeSystem/$validate-code?url=http://snomed.info/sct&version=http://snomed.info/sct/1234000008&code=257751006"
      }
    },
    {
      "request": {
        "method": "GET",
        "url": "CodeSystem/$validate-code?version=http://snomed.info/sct/1234000008&coding=http://snomed.info/sct|257751006&display=Baked potato 1"
      }
    }
  ]
}
```

Example using POST entries with a Parameters body:

```http
POST /fhir/
Content-Type: application/fhir+json

{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "resource": {
        "resourceType": "Parameters",
        "parameter": [
          { "name": "url", "valueUri": "http://snomed.info/sct" },
          { "name": "version", "valueString": "http://snomed.info/sct/1234000008" },
          { "name": "code", "valueCode": "257751006" }
        ]
      },
      "request": {
        "method": "POST",
        "url": "CodeSystem/$validate-code"
      }
    }
  ]
}
```

The response is a `batch-response` Bundle with one entry per request entry. Each successful entry contains
the same `Parameters` resource returned by a single `$validate-code` call. Unsupported batch entries (for example
`CodeSystem/$lookup`) receive an entry-level error with an `OperationOutcome`.

Instance-level validate-code is also supported in batch entries, for example:
`CodeSystem/sct_1234000008_20190731/$validate-code?code=257751006`
