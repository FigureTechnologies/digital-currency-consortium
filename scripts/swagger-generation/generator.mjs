import fetch from 'node-fetch';
import fs from 'fs';

const HOST = 'localhost:8080'; // TODO - set a realistic testnet host
const BASE_PATH = '/digital-currency-consortium';

const OUTPUT_FILE = 'dcc-spec.json';
const LOCAL_SWAGGER_ROUTE = 'http://localhost:8080/digital-currency-consortium/v2/api-docs'

fetch(LOCAL_SWAGGER_ROUTE)
    .then(response => {
        console.log('Retrieved OpenAPI spec...');
        return response.json();
    })
    .then(data => {
        console.log('Adjusting fields...');
        data['host'] = HOST;
        data['basePath'] = BASE_PATH;
        return data;
    })
    .then(data => {
        console.log('Writing spec to file...');
        fs.writeFile(OUTPUT_FILE, JSON.stringify(data, null, 2), err => {
            if (err) {
                console.error(err)
                return
            }
        })
        console.log(`Spec written to ${OUTPUT_FILE}`)
    });
