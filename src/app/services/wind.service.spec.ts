import { TestBed } from '@angular/core/testing';

import { WindService } from './wind.service';

describe('WindService', () => {
  let service: WindService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WindService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
