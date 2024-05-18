import { TestBed } from '@angular/core/testing';

import { StartlineService } from './startline.service';

describe('StartlineService', () => {
  let service: StartlineService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(StartlineService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
